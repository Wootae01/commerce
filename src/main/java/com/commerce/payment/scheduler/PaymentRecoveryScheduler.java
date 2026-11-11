package com.commerce.payment.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.commerce.common.enums.OrderStatus;
import com.commerce.order.domain.Orders;
import com.commerce.order.repository.OrderRepository;
import com.commerce.payment.domain.Payment;
import com.commerce.payment.dto.PayConfirmDTO;
import com.commerce.payment.external.TossApiException;
import com.commerce.payment.repository.PaymentRepository;
import com.commerce.payment.service.ApprovalResult;
import com.commerce.payment.service.CancelResult;
import com.commerce.payment.service.PaymentApprovalService;
import com.commerce.payment.service.PaymentCancelService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 보정 스케줄러. 토스 응답을 몰라 결과가 정해지지 않은 주문을 같은 멱등키(주문번호)로 다시 요청해 끝낸다.
 * <ul>
 *   <li>승인 보정: PAYMENT_PENDING 주문의 승인을 다시 요청한다. 이미 승인된 주문은 같은 응답을 받고,
 *       승인 요청이 토스에 도착하지 않았던 주문은 여기서 승인된다
 *   <li>취소 보정: CANCEL_REQUESTED(취소 처리 중) 주문의 취소를 다시 요청한다. 사용자가 이미 취소하기로 했으므로 조회하지 않고 다시 요청한다.
 *       토스가 거절하면 PAID로 되돌리고 에러 로그만 남긴다(사용자에게 알리지 않는다)
 * </ul>
 * 토스는 같은 멱등키의 요청을 한 번만 처리한다. 사용자 요청과 같은 주문을 동시에 처리해도 결과 반영은 주문 락 안에서
 * 결과가 아직 정해지지 않았을 때만 일어나므로 한 번만 반영된다.
 *
 * <p>두 보정은 @Scheduled를 따로 두어 각자 실행하고(한쪽의 예상 밖 오류가 다른 쪽을 막지 않는다), 아래 기준과 반복 처리는 같이 쓴다.
 * 기본 스케줄러 스레드가 1개라 동시에 돌지는 않는다.
 * 그 상태가 된 시각은 updated_at, 차례는 last_recovery_at(처리하지 않은 주문 → 처리한 지 오래된 주문)으로 정한다.
 * 시간으로 자동 취소·복원하지 않는다. 토스가 결과를 알려 줄 때만 주문을 바꾼다.
 * <ul>
 *   <li>토스와 통신하지 못한 주문(네트워크 오류·5xx)이 3건 연속이면 그 실행을 멈춘다. 장애 중인 토스에 요청을 계속 보내지 않는다
 *   <li>결과를 30분 넘게 모르면 에러 로그
 *   <li>24시간이 넘으면 자동 처리에서 빼고 에러 로그. 멱등키 유효 기간(15일)보다 짧게 잡았다. 사람이 확인해야 한다
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryScheduler {

	// 사용자 요청이 아직 처리 중일 수 있으므로 그 상태가 된 지 이만큼 지난 주문만 다시 요청한다
	private static final Duration RECOVERY_DELAY = Duration.ofMinutes(1);
	// 그 상태가 된 지 이만큼 지나도 결과를 모르면 에러 로그
	private static final Duration LONG_UNKNOWN_ALERT_AFTER = Duration.ofMinutes(30);
	// 그 상태가 된 지 이만큼 지나면 자동 처리를 그만두고 사람에게 넘긴다
	private static final Duration RECOVERY_GIVE_UP_AFTER = Duration.ofHours(24);
	private static final int MAX_ORDERS_PER_RUN = 100;
	private static final int MAX_CONSECUTIVE_UNREACHABLE = 3;
	// 바로 재요청하지 않는다. 결과를 모르면 다음 실행에서 다시 요청한다
	private static final int RECOVERY_MAX_RETRIES = 0;

	private final OrderRepository orderRepository;
	private final PaymentApprovalService paymentApprovalService;
	private final PaymentCancelService paymentCancelService;
	private final PaymentRepository paymentRepository;

	@Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
	public void recoverPendingPayments() {
		recoverAll(Target.PAYMENT, this::retryApproval);
	}

	@Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
	public void recoverCancelRequests() {
		recoverAll(Target.CANCEL, this::retryCancel);
	}

	private Attempt retryApproval(Orders order, Payment payment) {
		PayConfirmDTO req = new PayConfirmDTO(payment.getPaymentKey(), order.getOrderNumber(), payment.getAmount());
		ApprovalResult result = paymentApprovalService.approve(req, RECOVERY_MAX_RETRIES);
		return new Attempt(result.status(), result.tossUnreachable());
	}

	private Attempt retryCancel(Orders order, Payment payment) {
		try {
			CancelResult result = paymentCancelService.cancel(order, payment.getPaymentKey(), RECOVERY_MAX_RETRIES);
			return new Attempt(result.status(), result.tossUnreachable());
		} catch (TossApiException e) {
			// 토스가 거절해 PAID로 되돌렸다. 에러 로그는 PaymentCancelService가 남긴다
			return new Attempt(OrderStatus.PAID, false);
		}
	}

	private void recoverAll(Target target, Retry retry) {
		LocalDateTime now = LocalDateTime.now();
		reportExpired(target, now);

		List<Orders> orders = orderRepository.findRecoveryTargets(target.status,
			now.minus(RECOVERY_DELAY), now.minus(RECOVERY_GIVE_UP_AFTER), Limit.of(MAX_ORDERS_PER_RUN));
		if (orders.isEmpty()) {
			return;
		}

		log.info("{} 보정 시작: 대상 {}건", target.label, orders.size());
		Map<Long, Payment> paymentByOrderId = paymentRepository.findByOrderIdIn(
				orders.stream().map(Orders::getId).toList()).stream()
			.collect(Collectors.toMap(payment -> payment.getOrder().getId(), Function.identity()));
		int consecutiveUnreachable = 0;
		for (int i = 0; i < orders.size(); i++) {
			Orders order = orders.get(i);
			boolean unreachable = recover(target, retry, order, paymentByOrderId.get(order.getId()), now);
			consecutiveUnreachable = unreachable ? consecutiveUnreachable + 1 : 0;
			if (consecutiveUnreachable >= MAX_CONSECUTIVE_UNREACHABLE) {
				// 처리하지 못한 주문은 last_recovery_at이 그대로라 다음 실행에서 먼저 처리된다
				log.warn("토스 연결 실패 {}번 연속, 이번 {} 보정 중단: 남은 {}건은 다음 실행에서 처리",
					consecutiveUnreachable, target.label, orders.size() - i - 1);
				return;
			}
		}
	}

	// 토스와 통신하지 못했으면 true
	private boolean recover(Target target, Retry retry, Orders order, Payment payment, LocalDateTime now) {
		LocalDateTime previousRecoveryAt = order.getLastRecoveryAt();
		try {
			// 차례를 먼저 넘긴다. 아래에서 실패해도 같은 주문이 앞자리를 계속 차지하지 않는다
			orderRepository.markRecoveryAttempted(order.getId(), now);
			// 결제를 시작할 때 결제가 함께 만들어지므로 없으면 데이터가 어긋난 것이다
			if (payment == null) {
				throw new IllegalStateException("보정 대상 주문에 결제 정보가 없습니다.");
			}
			Attempt attempt = retry.attempt(order, payment);
			log.info("{} 보정 결과: orderNumber={}, status={}", target.label, order.getOrderNumber(), attempt.status());
			if (attempt.status() == target.status) {
				reportLongUnknown(target, order, previousRecoveryAt, now);
			}
			return attempt.tossUnreachable();
		} catch (Exception e) {
			// 한 주문의 실패로 나머지 주문의 보정이 멈추지 않게 한다. 주문은 그대로 남아 다음 실행에서 다시 처리된다
			log.error("{} 보정 실패: orderNumber={}", target.label, order.getOrderNumber(), e);
			// 처리 시각은 이미 남았을 수 있으므로, 여기서 알리지 않으면 30분 로그를 놓친다
			reportLongUnknown(target, order, previousRecoveryAt, now);
			return false;
		}
	}

	// 30분을 넘긴 뒤 첫 처리에서만 남긴다 (이전 처리가 30분이 되기 전이었으면 처음 넘긴 것)
	private void reportLongUnknown(Target target, Orders order, LocalDateTime previousRecoveryAt, LocalDateTime now) {
		LocalDateTime alertAt = order.getUpdatedAt().plus(LONG_UNKNOWN_ALERT_AFTER);
		boolean firstAttemptAfterAlertAt = !now.isBefore(alertAt)
			&& (previousRecoveryAt == null || previousRecoveryAt.isBefore(alertAt));
		if (firstAttemptAfterAlertAt) {
			log.error("{} 결과를 30분 넘게 모름, 확인 필요: orderNumber={}, since={}",
				target.label, order.getOrderNumber(), order.getUpdatedAt());
		}
	}

	// 24시간이 넘어 자동 처리에서 빠지는 주문을 한 번만 알린다.
	// 자동 처리 중에는 last_recovery_at이 24시간이 되기 전이므로, 24시간 뒤의 값이면 이미 알린 주문이다
	private void reportExpired(Target target, LocalDateTime now) {
		List<Orders> expired = orderRepository.findRecoveryExpired(target.status, now.minus(RECOVERY_GIVE_UP_AFTER));
		for (Orders order : expired) {
			LocalDateTime expiredAt = order.getUpdatedAt().plus(RECOVERY_GIVE_UP_AFTER);
			if (order.getLastRecoveryAt() != null && !order.getLastRecoveryAt().isBefore(expiredAt)) {
				continue;
			}
			log.error("{} 결과를 24시간 넘게 모름, 자동 처리에서 제외 — 사람이 확인해야 함: orderNumber={}, since={}",
				target.label, order.getOrderNumber(), order.getUpdatedAt());
			orderRepository.markRecoveryAttempted(order.getId(), now);
		}
	}

	// 보정할 주문의 상태(결과를 모르는 상태)와 로그에 쓸 이름
	private enum Target {
		PAYMENT(OrderStatus.PAYMENT_PENDING, "결제"),
		CANCEL(OrderStatus.CANCEL_REQUESTED, "취소");

		private final OrderStatus status;
		private final String label;

		Target(OrderStatus status, String label) {
			this.status = status;
			this.label = label;
		}
	}

	// 한 주문을 같은 멱등키로 다시 요청하고 결과를 반영한다
	@FunctionalInterface
	private interface Retry {
		Attempt attempt(Orders order, Payment payment);
	}

	// 반영 후 주문 상태, 토스와 통신하지 못했는지
	private record Attempt(OrderStatus status, boolean tossUnreachable) {
	}
}
