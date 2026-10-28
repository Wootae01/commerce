package com.commerce.payment.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.commerce.common.enums.OrderStatus;
import com.commerce.order.domain.Orders;
import com.commerce.order.repository.OrderRepository;
import com.commerce.payment.dto.PayConfirmDTO;
import com.commerce.payment.service.PaymentApprovalService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 보정 스케줄러.
 * 승인 결과를 모른 채 PAYMENT_PENDING으로 남은 주문을 같은 멱등키(주문번호)로 다시 승인 요청해 확정한다.
 * 토스는 같은 멱등키의 승인을 한 번만 처리하므로, 이미 승인된 주문은 같은 응답을 받고, 승인 요청이 토스에 도착하지 않았던 주문은 여기서 승인된다.
 * 확정 요청과 같은 주문을 동시에 처리해도 결과 반영은 주문 락 안에서 PENDING일 때만 일어나므로 한 번만 반영된다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentRecoveryScheduler {

	// 확정 요청이 아직 처리 중일 수 있으므로 PENDING이 된 지 이만큼 지난 주문만 다시 요청한다
	private static final long PENDING_MINUTES_BEFORE_RECOVERY = 1;
	private static final int MAX_ORDERS_PER_RUN = 100;

	private final OrderRepository orderRepository;
	private final PaymentApprovalService paymentApprovalService;

	@Scheduled(fixedDelay = 60_000)
	public void recoverPendingPayments() {
		LocalDateTime updatedBefore = LocalDateTime.now().minusMinutes(PENDING_MINUTES_BEFORE_RECOVERY);
		List<Orders> orders = orderRepository.findOldestByStatusUpdatedBefore(
			OrderStatus.PAYMENT_PENDING, updatedBefore, Limit.of(MAX_ORDERS_PER_RUN));
		if (orders.isEmpty()) {
			return;
		}

		log.info("결제 보정 시작: 대상 {}건", orders.size());
		for (Orders order : orders) {
			recover(order);
		}
	}

	private void recover(Orders order) {
		PayConfirmDTO req = new PayConfirmDTO(order.getPaymentKey(), order.getOrderNumber(), order.getFinalPrice());
		try {
			// 바로 재요청하지 않는다. 결과를 모르면 다음 실행에서 다시 요청한다
			OrderStatus result = paymentApprovalService.approve(req, 1);
			log.info("결제 보정 결과: orderNumber={}, status={}", order.getOrderNumber(), result);
		} catch (Exception e) {
			// 한 주문의 실패로 나머지 주문의 보정이 멈추지 않게 한다. 주문은 PENDING으로 남아 다음 실행에서 다시 처리된다
			log.error("결제 보정 실패: orderNumber={}", order.getOrderNumber(), e);
		}
	}
}
