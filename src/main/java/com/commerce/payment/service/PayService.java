package com.commerce.payment.service;

import com.commerce.common.enums.OrderStatus;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.exception.ApiException;
import com.commerce.order.domain.Orders;
import com.commerce.order.service.OrderService;
import com.commerce.payment.dto.CancelResponseDTO;
import com.commerce.payment.dto.PayConfirmDTO;
import com.commerce.payment.external.TossPaymentClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayService {

	private final OrderService orderService;
	private final PaymentTxService paymentTxService;
	private final TossPaymentClient tossPaymentClient;
	private final PaymentApprovalService paymentApprovalService;

	public CancelResponseDTO cancel(String orderNumber, String cancelReason, Long userId) {
		Orders order = orderService.findMyOrder(orderNumber, userId);

		// 1. 주문 잠금 + 상태 전이 (→ CANCEL_REQUESTED) - 동시 취소 요청 방지
		String paymentKey = paymentTxService.beginCancel(order.getId());

		// paymentKey 없으면 결제를 하지 않은 주문 — 바로 취소
		if (paymentKey == null || paymentKey.isEmpty()) {
			paymentTxService.applyCancelSuccess(order.getId(), false);
			return new CancelResponseDTO(true, orderNumber, LocalDateTime.now(), 0, "결제 안함", null);
		}

		// 2. 토스 취소 요청
		JsonNode response;
		try {
			Map<String, Object> data = new HashMap<>();
			data.put("cancelReason", cancelReason);
			response = tossPaymentClient.cancel(paymentKey, data);
		} catch (Exception e) {
			// 토스 취소 실패 — 주문 상태 복원
			log.error("토스 취소 실패, 주문 상태 복원: orderNumber={}", orderNumber, e);
			paymentTxService.revertCancelRequest(order.getId());
			throw e;
		}

		// 3. 취소 성공 — 상태 변경 + 재고 복원 (단일 트랜잭션)
		paymentTxService.applyCancelSuccess(order.getId(), true);

		// 4. dto 반환
		String method = response.path("method").asText(); // 결제 수단
		int cancelAmount = response.path("cancels").path(0).path("cancelAmount").asInt(); // 취소 금액
		return new CancelResponseDTO(true, orderNumber, LocalDateTime.now(), cancelAmount, method, null);
	}

	// 확정 후 주문 상태(PAID / PAYMENT_PENDING / CANCELED)를 돌려준다
	public OrderStatus confirm(PayConfirmDTO req, Long userId) {
		log.info("confirm start: orderId={}, paymentKey={}, amount={}",
			req.getOrderId(), req.getPaymentKey(), req.getAmount());

		// 1. 주문 조회 (본인 주문만)
		Orders order = orderService.findMyOrder(req.getOrderId(), userId);

		// 2. 같은 paymentKey로 이미 확정을 시작한 주문(새로고침·재전송)이면 토스를 다시 부르지 않고 지금 상태를 돌려준다
		if (isAlreadyRequested(order, req)) {
			log.info("이미 확정을 시작한 주문, 지금 상태 반환: orderNumber={}, status={}", order.getOrderNumber(), order.getOrderStatus());
			return order.getOrderStatus();
		}

		// 3. 검증
		validatePayment(order, req);

		// 4. 재고 차감 (토스 호출 전)
		paymentTxService.lockAndDeductStock(req.getOrderId(), req.getPaymentKey());

		// 5. 토스 승인 요청 + 결과 반영. 응답을 모르면 같은 멱등키로 1번 다시 요청한다
		return paymentApprovalService.approve(req, 2);
	}

	private boolean isAlreadyRequested(Orders order, PayConfirmDTO req) {
		OrderStatus status = order.getOrderStatus();
		return (status == OrderStatus.PAID || status == OrderStatus.PAYMENT_PENDING)
			&& Objects.equals(order.getPaymentKey(), req.getPaymentKey());
	}

	private void validatePayment(Orders order, PayConfirmDTO req) {
		log.info("order loaded: orderNumber={}, status={}, finalPrice={}",
			order.getOrderNumber(), order.getOrderStatus(), order.getFinalPrice());

		// 1. 상태 확인
		if (order.getOrderStatus() != OrderStatus.READY) {
			log.warn("결제 가능한 상태가 아닙니다. status={}", order.getOrderStatus());
			throw new ApiException(GeneralResponseCode.PAYMENT_NOT_PAYABLE);
		}

		// 2. 금액 검증
		if (order.getFinalPrice() != req.getAmount()) {
			log.info("결제 금액이 일치하지 않습니다. orderFinalPrice: {}, reqAmount: {}", order.getFinalPrice(), req.getAmount());
			throw new ApiException(GeneralResponseCode.PAYMENT_AMOUNT_INVALID);
		}
	}

}
