package com.commerce.payment.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.commerce.common.enums.OrderStatus;
import com.commerce.order.domain.Orders;
import com.commerce.order.repository.OrderRepository;
import com.commerce.payment.dto.PayConfirmDTO;
import com.commerce.payment.service.ApprovalResult;
import com.commerce.payment.service.PaymentApprovalService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 보정 스케줄러.
 * 승인 결과를 모른 채 PAYMENT_PENDING으로 남은 주문을 같은 멱등키(주문번호)로 다시 승인 요청해 확정한다.
 * 토스는 같은 멱등키의 승인을 한 번만 처리하므로, 이미 승인된 주문은 같은 응답을 받고, 승인 요청이 토스에 도착하지 않았던 주문은 여기서 승인된다.
 * 확정 요청과 같은 주문을 동시에 처리해도 결과 반영은 주문 락 안에서 PENDING일 때만 일어나므로 한 번만 반영된다.
 *
 * <p>PENDING이 된 시각은 updated_at, 차례는 last_recovery_at(처리하지 않은 주문 → 처리한 지 오래된 주문)으로 정한다.
 * 시간으로 자동 취소하지 않는다. 토스가 승인·실패를 알려 줄 때만 주문을 바꾼다.
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

	// 확정 요청이 아직 처리 중일 수 있으므로 PENDING이 된 지 이만큼 지난 주문만 다시 요청한다
	private static final Duration RECOVERY_DELAY = Duration.ofMinutes(1);
	private static final Duration LONG_UNKNOWN = Duration.ofMinutes(30);
	private static final Duration RECOVERY_LIMIT = Duration.ofHours(24);
	private static final int MAX_ORDERS_PER_RUN = 100;
	private static final int MAX_CONSECUTIVE_UNREACHABLE = 3;
	// 바로 재요청하지 않는다. 결과를 모르면 다음 실행에서 다시 요청한다
	private static final int RECOVERY_MAX_RETRIES = 0;

	private final OrderRepository orderRepository;
	private final PaymentApprovalService paymentApprovalService;

	@Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
	public void recoverPendingPayments() {
		LocalDateTime now = LocalDateTime.now();
		reportExpired(now);

		List<Orders> orders = orderRepository.findRecoveryTargets(OrderStatus.PAYMENT_PENDING,
			now.minus(RECOVERY_DELAY), now.minus(RECOVERY_LIMIT), Limit.of(MAX_ORDERS_PER_RUN));
		if (orders.isEmpty()) {
			return;
		}

		log.info("결제 보정 시작: 대상 {}건", orders.size());
		int consecutiveUnreachable = 0;
		for (int i = 0; i < orders.size(); i++) {
			boolean unreachable = recover(orders.get(i), now);
			consecutiveUnreachable = unreachable ? consecutiveUnreachable + 1 : 0;
			if (consecutiveUnreachable >= MAX_CONSECUTIVE_UNREACHABLE) {
				// 처리하지 못한 주문은 last_recovery_at이 그대로라 다음 실행에서 먼저 처리된다
				log.warn("토스 연결 실패 {}번 연속, 이번 보정 중단: 남은 {}건은 다음 실행에서 처리",
					consecutiveUnreachable, orders.size() - i - 1);
				return;
			}
		}
	}

	// 토스와 통신하지 못했으면 true
	private boolean recover(Orders order, LocalDateTime now) {
		LocalDateTime previousRecoveryAt = order.getLastRecoveryAt();
		orderRepository.markRecoveryAttempted(order.getId(), now);

		PayConfirmDTO req = new PayConfirmDTO(order.getPaymentKey(), order.getOrderNumber(), order.getFinalPrice());
		try {
			ApprovalResult result = paymentApprovalService.approve(req, RECOVERY_MAX_RETRIES);
			log.info("결제 보정 결과: orderNumber={}, status={}", order.getOrderNumber(), result.status());
			if (result.status() == OrderStatus.PAYMENT_PENDING) {
				reportLongUnknown(order, previousRecoveryAt, now);
			}
			return result.tossUnreachable();
		} catch (Exception e) {
			// 한 주문의 실패로 나머지 주문의 보정이 멈추지 않게 한다. 주문은 PENDING으로 남아 다음 실행에서 다시 처리된다
			log.error("결제 보정 실패: orderNumber={}", order.getOrderNumber(), e);
			return false;
		}
	}

	// 30분을 넘긴 뒤 첫 처리에서만 남긴다 (이전 처리가 30분이 되기 전이었으면 처음 넘긴 것)
	private void reportLongUnknown(Orders order, LocalDateTime previousRecoveryAt, LocalDateTime now) {
		LocalDateTime longUnknownAt = order.getUpdatedAt().plus(LONG_UNKNOWN);
		boolean crossedNow = !now.isBefore(longUnknownAt)
			&& (previousRecoveryAt == null || previousRecoveryAt.isBefore(longUnknownAt));
		if (crossedNow) {
			log.error("결제 결과를 30분 넘게 모름, 확인 필요: orderNumber={}, pendingSince={}",
				order.getOrderNumber(), order.getUpdatedAt());
		}
	}

	// 24시간이 넘어 자동 처리에서 빠지는 주문을 한 번만 알린다.
	// 자동 처리 중에는 last_recovery_at이 24시간이 되기 전이므로, 24시간 뒤의 값이면 이미 알린 주문이다
	private void reportExpired(LocalDateTime now) {
		List<Orders> expired = orderRepository.findByStatusUpdatedBefore(OrderStatus.PAYMENT_PENDING,
			now.minus(RECOVERY_LIMIT));
		for (Orders order : expired) {
			LocalDateTime expiredAt = order.getUpdatedAt().plus(RECOVERY_LIMIT);
			if (order.getLastRecoveryAt() != null && !order.getLastRecoveryAt().isBefore(expiredAt)) {
				continue;
			}
			log.error("결제 결과를 24시간 넘게 모름, 자동 처리에서 제외 — 사람이 확인해야 함: orderNumber={}, pendingSince={}",
				order.getOrderNumber(), order.getUpdatedAt());
			orderRepository.markRecoveryAttempted(order.getId(), now);
		}
	}
}
