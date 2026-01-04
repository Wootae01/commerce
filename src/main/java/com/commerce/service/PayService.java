package com.commerce.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.commerce.domain.OrderProduct;
import com.commerce.domain.Orders;
import com.commerce.domain.enums.OrderStatus;
import com.commerce.domain.enums.OrderType;
import com.commerce.domain.enums.PaymentType;
import com.commerce.dto.CancelResponseDTO;
import com.commerce.dto.PayConfirmDTO;
import com.commerce.repository.CartProductRepository;
import com.commerce.external.TossPaymentClient;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayService {

	private final OrderService orderService;
	private final PaymentTxService paymentTxService;
	private final CartProductRepository cartProductRepository;
	private final TossPaymentClient tossPaymentClient;

	@Transactional
	public CancelResponseDTO cancel(String orderNumber, String cancelReason) {
		Orders order = orderService.findByOrderNumber(orderNumber);

		// 1. 주문 상태 확인
		OrderStatus status = order.getOrderStatus();
		if (!(status == OrderStatus.PAID || status == OrderStatus.READY)) {
			throw new IllegalStateException("현재 상태에서는 주문 취소를할 수 없습니다.");
		}

		if (order.getPaymentKey() == null || order.getPaymentKey().isBlank()) {
			throw new IllegalStateException("paymentKey가 없어 주문 취소를 할 수 없습니다.");
		}

		// 2. 토스 요청
		Map<String, Object> data = new HashMap<>();
		data.put("cancelReason", cancelReason);

		JsonNode response = tossPaymentClient.cancel(order.getPaymentKey(), data);

		// 3. 성공 시  상태 변경, 재고 수정
		order.setOrderStatus(OrderStatus.CANCELED);
		order.getOrderProducts().forEach((
			orderProduct -> orderProduct.getProduct().increaseStock(orderProduct.getQuantity())
		));

		// 4. dto 반환
		String method = response.path("method").asText();
		int cancelAmount = response.path("cancels").path(0).path("cancelAmount").asInt();
		return new CancelResponseDTO(true, orderNumber, LocalDateTime.now(), cancelAmount, method, null);
	}

	public void confirm(PayConfirmDTO req, Long userId) {
		log.info("confirm start: orderId={}, paymentKey={}, amount={}",
			req.getOrderId(), req.getPaymentKey(), req.getAmount());

		// 1. 검증
		paymentTxService.validatePayment(req, userId);

		// 2. 토스 요청
		JsonNode tossResponse = confirmWithToss(req);
		log.info("toss confirm ok: {}", tossResponse);

		// 3. 재고 차감, 장바구니 삭제, 결제 일시, 상태 변경
		paymentTxService.applyPayment(req.getOrderId(), tossResponse, userId, req.getPaymentKey());

	}

	private JsonNode confirmWithToss(PayConfirmDTO req) {
		JsonNode tossResponse;
		try {
			tossResponse = tossPaymentClient.confirm(req);
		} catch (WebClientResponseException e) {
			log.warn("payment confirm failed in service: orderId={}, amount={}, status={}",
				req.getOrderId(), req.getAmount(), e.getStatusCode().value());
			orderService.deleteOrderByOrderNumber(req.getOrderId());
			throw new ResponseStatusException(e.getStatusCode(), "토스 승인 실패");
		} catch (Exception e) {
			log.error("toss confirm exception: orderId={}, amount={}",
				req.getOrderId(), req.getAmount(), e);
			orderService.deleteOrderByOrderNumber(req.getOrderId());
			throw e;
		}
		return tossResponse;
	}

}
