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
import com.commerce.util.TossPaymentClient;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayService {

	private final OrderService orderService;
	private final WebClient tossWebClient;
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

	@Transactional
	public void confirm(PayConfirmDTO req, Long userId) {
		log.info("confirm start: orderId={}, paymentKey={}, amount={}",
			req.getOrderId(), req.getPaymentKey(), req.getAmount());

		// 1, 주문 조회
		Orders order = orderService.findByOrderNumber(req.getOrderId());
		log.info("order loaded: orderNumber={}, status={}, finalPrice={}",
			order.getOrderNumber(), order.getOrderStatus(), order.getFinalPrice());

		if (!Objects.equals(order.getUser().getId(), userId)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 주문이 아닙니다.");
		}

		// 2. 중복 주문 방지
		if (order.getOrderStatus() == OrderStatus.PAID) {
			if (Objects.equals(order.getPaymentKey(), req.getPaymentKey())) return;
			log.info("이미 처리도니 주문입니다.");
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 주문입니다.");
		}

		// 3. 상태 확인
		if (order.getOrderStatus() != OrderStatus.READY) {
			log.info("결제 가능한 상태가 아닙니다.");
			throw new ResponseStatusException(HttpStatus.CONFLICT, "결제 가능한 상태가 아닙니다.");
		}

		// 4. 금액 검증
		if (req.getAmount() == null || order.getFinalPrice() != req.getAmount()) {
			log.info("결제 금액이 일치하지 않습니다.");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다");
		}
		log.info("calling toss confirm");

		// 5. 토스 요청
		JsonNode tossResponse;

		try {
			tossResponse = tossWebClient.post()
				.uri("/v1/payments/confirm")
				.bodyValue(new PayConfirmDTO(req.getPaymentKey(), req.getOrderId(), req.getAmount()))
				.retrieve()
				.onStatus(HttpStatusCode::isError, res ->
					res.bodyToMono(String.class)
						.defaultIfEmpty("")
						.map(body -> new ResponseStatusException(res.statusCode(),
							"toss confirm error: " + body))
				)
				.bodyToMono(JsonNode.class)
				.block();
		} catch (WebClientResponseException e) {
			log.error("toss confirm failed: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
			orderService.deleteOrderByOrderNumber(req.getOrderId());
			throw new ResponseStatusException(e.getStatusCode(), "토스 승인 실패");
		} catch (Exception e) {
			log.error("toss confirm exception", e);
			orderService.deleteOrderByOrderNumber(req.getOrderId());
			throw e;
		}

		log.info("toss confirm ok: {}", tossResponse);

		// 6. 재고 차감, 장바구니 삭제, 결제 일시, 상태 변경
		order.setOrderStatus(OrderStatus.PAID);
		order.setPaymentType(PaymentType.fromTossMethod(tossResponse.path("method").asText()));
		order.setPaymentKey(req.getPaymentKey());

		String approvedAtStr = tossResponse.path("approvedAt").asText();

		// 결제 일시
		LocalDateTime approvedAt = null;
		if (approvedAtStr != null && !approvedAtStr.isBlank()) {
			approvedAt = OffsetDateTime.parse(approvedAtStr).toLocalDateTime();
		}

		order.setApprovedAt(approvedAt);

		// 재고 차감
		List<OrderProduct> orderProducts = order.getOrderProducts();
		for (OrderProduct orderProduct : orderProducts) {
			orderProduct.getProduct().decreaseStock(orderProduct.getQuantity());
		}

		// 장바구니 삭제
		if (order.getOrderType() == OrderType.CART) {
			List<Long> ids = order.getCartProductIds();
			if (!ids.isEmpty()) {
				cartProductRepository.deleteSelectedFromUserCart(ids, userId);
			}
		}
	}

}
