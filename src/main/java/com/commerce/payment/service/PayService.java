package com.commerce.payment.service;

import com.commerce.order.domain.OrderProduct;
import com.commerce.order.domain.Orders;
import com.commerce.product.domain.Product;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.OrderType;
import com.commerce.common.enums.PaymentType;
import com.commerce.payment.dto.CancelResponseDTO;
import com.commerce.payment.dto.PayConfirmDTO;
import com.commerce.common.exception.EntityNotFoundException;
import com.commerce.payment.external.TossPaymentClient;
import com.commerce.cart.repository.CartProductRepository;
import com.commerce.order.repository.OrderRepository;
import com.commerce.product.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

import com.commerce.order.service.OrderService;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayService {

	private final OrderService orderService;
	private final PaymentTxService paymentTxService;
	private final CartProductRepository cartProductRepository;
	private final TossPaymentClient tossPaymentClient;
	private final WebClient tossWebClient;
	private final OrderRepository orderRepository;

	public CancelResponseDTO cancel(String orderNumber, String cancelReason) {
		Orders order = orderService.findByOrderNumber(orderNumber);

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
		} catch (Exception e) {
			paymentTxService.restoreStockOnTossFailure(req.getOrderId());
			throw e;
		}
		log.info("request toss duration in confirm: {}", System.currentTimeMillis() - startTime);

		// 4. 장바구니 삭제, 결제 일시, 상태 변경
		paymentTxService.applyPaymentSuccess(req.getOrderId(), tossResponse, userId, req.getPaymentKey());
	}

	// 트랜잭션 범위 축소 전 이전 코드. 성능 비교 위해 남김
	@Transactional
	public void confirm_before(PayConfirmDTO req, Long userId) {
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
			log.info("이미 처리된 주문입니다.");
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 주문입니다.");
		}

		// 3. 상태 확인
		if (order.getOrderStatus() != OrderStatus.READY) {
			log.info("결제 가능한 상태가 아닙니다.");
			throw new ResponseStatusException(HttpStatus.CONFLICT, "결제 가능한 상태가 아닙니다.");
		}

		// 4. 금액 검증
		if (order.getFinalPrice() != req.getAmount()) {
			log.info("결제 금액이 일치하지 않습니다.");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다");
		}
		log.info("calling toss confirm");

		// 5. 토스 요청
		JsonNode tossResponse;
		long startTime = System.currentTimeMillis();
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
		log.info("request toss duration in confirm_before: {}", System.currentTimeMillis() - startTime);
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
		Orders orderWithProducts = orderRepository.findByOrderNumberWithProduct(req.getOrderId())
			.orElseThrow();
		for (OrderProduct orderProduct : orderWithProducts.getOrderProducts()) {
			if (orderProduct.getProductOption() != null) {
				orderProduct.getProductOption().deductStock(orderProduct.getQuantity());
			} else {
				orderProduct.getProduct().deductStock(orderProduct.getQuantity());
			}
		}

		// 장바구니 삭제
		if (order.getOrderType() == OrderType.CART) {
			List<Long> ids = order.getCartProductIds();
			if (!ids.isEmpty()) {
				cartProductRepository.deleteSelectedFromUserCart(ids, userId);
			}
		}
	}

	public void validatePayment(PayConfirmDTO req, Long userId) {
		// 1, 주문 조회
		Orders order = orderRepository.findByOrderNumber(req.getOrderId())
			.orElseThrow(() -> new EntityNotFoundException("해당 주문이 존재하지 않습니다."));

		log.info("order loaded: orderNumber={}, status={}, finalPrice={}",
			order.getOrderNumber(), order.getOrderStatus(), order.getFinalPrice());

		if (!Objects.equals(order.getUser().getId(), userId)) {
			log.warn("본인 주문이 아닙니다. orderId: {}, orderUserId={}, userId={}", order.getOrderNumber(), order.getUser().getId(), userId);
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 주문이 아닙니다.");
		}

		// 2. 중복 주문 방지
		if (order.getOrderStatus() == OrderStatus.PAID && Objects.equals(order.getPaymentKey(), req.getPaymentKey())) {
			log.warn("이미 처리된 주문입니다.");
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리된 주문입니다.");
		}

		// 3. 상태 확인
		if (order.getOrderStatus() != OrderStatus.READY) {
			log.warn("결제 가능한 상태가 아닙니다. status={}", order.getOrderStatus());
			throw new ResponseStatusException(HttpStatus.CONFLICT, "결제 가능한 상태가 아닙니다.");
		}

		// 4. 금액 검증
		if (order.getFinalPrice() != req.getAmount()) {
			log.info("결제 금액이 일치하지 않습니다. orderFinalPrice: {}, reqAmount: {}", order.getFinalPrice(), req.getAmount());
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "결제 금액이 일치하지 않습니다");
		}
	}

	private JsonNode confirmWithToss(PayConfirmDTO req) {
		try {
			return tossPaymentClient.confirm(req);
		} catch (WebClientResponseException e) {
			log.warn("payment confirm failed: orderId={}, status={}", req.getOrderId(), e.getStatusCode().value());
			throw new ResponseStatusException(e.getStatusCode(), "토스 승인 실패");
		} catch (Exception e) {
			log.error("toss confirm exception: orderId={}", req.getOrderId(), e);
			throw e;
		}
	}

}
