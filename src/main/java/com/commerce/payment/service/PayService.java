package com.commerce.payment.service;

import com.commerce.common.enums.OrderStatus;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.exception.ApiException;
import com.commerce.order.domain.Orders;
import com.commerce.order.repository.OrderRepository;
import com.commerce.order.service.OrderService;
import com.commerce.payment.dto.CancelResponseDTO;
import com.commerce.payment.dto.PayConfirmDTO;
import com.commerce.payment.external.TossPaymentClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;

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
	private final OrderRepository orderRepository;

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

	public void confirm(PayConfirmDTO req, Long userId) {
		log.info("confirm start: orderId={}, paymentKey={}, amount={}",
			req.getOrderId(), req.getPaymentKey(), req.getAmount());

		// 1. 검증
		validatePayment(req, userId);

		// 2. 재고 차감 (토스 호출 전)
		paymentTxService.lockAndDeductStock(req.getOrderId());

		// 3. 토스 요청
		long startTime = System.currentTimeMillis();
		JsonNode tossResponse;
		try {
			tossResponse = confirmWithToss(req);
		} catch (WebClientRequestException e) {
			handleAmbiguousFailure(req, userId);
			throw e;
		} catch (Exception e) {
			paymentTxService.restoreStockOnTossFailure(req.getOrderId());
			throw e;
		}
		log.info("request toss duration in confirm: {}", System.currentTimeMillis() - startTime);

		// 4. 장바구니 삭제, 결제 일시, 상태 변경
		paymentTxService.applyPaymentSuccess(req.getOrderId(), tossResponse, req.getPaymentKey());
	}

	public void validatePayment(PayConfirmDTO req, Long userId) {
		// 1, 주문 조회
		Orders order = orderRepository.findByOrderNumber(req.getOrderId())
			.orElseThrow(() -> new ApiException(GeneralResponseCode.ORDER_NOT_FOUND));

		log.info("order loaded: orderNumber={}, status={}, finalPrice={}",
			order.getOrderNumber(), order.getOrderStatus(), order.getFinalPrice());

		if (!Objects.equals(order.getUser().getId(), userId)) {
			log.warn("본인 주문이 아닙니다. orderId: {}, orderUserId={}, userId={}", order.getOrderNumber(), order.getUser().getId(), userId);
			throw new ApiException(GeneralResponseCode.ORDER_ACCESS_DENIED);
		}

		// 2. 중복 주문 방지
		if (order.getOrderStatus() == OrderStatus.PAID && Objects.equals(order.getPaymentKey(), req.getPaymentKey())) {
			log.warn("이미 처리된 주문입니다.");
			throw new ApiException(GeneralResponseCode.PAYMENT_ALREADY_PROCESSED);
		}

		// 3. 상태 확인
		if (order.getOrderStatus() != OrderStatus.READY) {
			log.warn("결제 가능한 상태가 아닙니다. status={}", order.getOrderStatus());
			throw new ApiException(GeneralResponseCode.PAYMENT_NOT_PAYABLE);
		}

		// 4. 금액 검증
		if (order.getFinalPrice() != req.getAmount()) {
			log.info("결제 금액이 일치하지 않습니다. orderFinalPrice: {}, reqAmount: {}", order.getFinalPrice(), req.getAmount());
			throw new ApiException(GeneralResponseCode.PAYMENT_AMOUNT_INVALID);
		}
	}

	private JsonNode confirmWithToss(PayConfirmDTO req) {
		try {
			return tossPaymentClient.confirm(req);
		} catch (Exception e) {
			log.error("toss confirm exception: orderId={}", req.getOrderId(), e);
			throw e;
		}
	}

	private void handleAmbiguousFailure(PayConfirmDTO req, Long userId) {
		log.warn("응답 유실 가능성, Toss 결제 상태 조회: orderId={}", req.getOrderId());
		try {
			JsonNode payment = tossPaymentClient.getPayment(req.getOrderId());
			String status = payment.path("status").asText();
			if ("DONE".equals(status)) {
				log.info("Toss 결제 확인됨, 승인 처리: orderId={}", req.getOrderId());
				paymentTxService.applyPaymentSuccess(req.getOrderId(), payment, req.getPaymentKey());
			} else {
				log.info("Toss 미결제 확인, 재고 복원: orderId={}, status={}", req.getOrderId(), status);
				paymentTxService.restoreStockOnTossFailure(req.getOrderId());
			}
		} catch (Exception e) {
			log.error("Toss 조회 실패, 수동 처리 필요: orderId={}", req.getOrderId(), e);
		}
	}

}
