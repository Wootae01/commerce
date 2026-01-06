package com.commerce.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
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
import com.commerce.domain.Product;
import com.commerce.domain.enums.OrderStatus;
import com.commerce.domain.enums.OrderType;
import com.commerce.domain.enums.PaymentType;
import com.commerce.dto.PayConfirmDTO;
import com.commerce.repository.CartProductRepository;
import com.commerce.repository.OrderRepository;
import com.commerce.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTxService {

	private final OrderRepository orderRepository;
	private final CartProductRepository cartProductRepository;
	private final ProductRepository productRepository;

	// 재고 차감, 장바구니 삭제, 결제 일시, 상태 변경
	@Transactional
	public void applyPayment(String orderNumber, JsonNode tossResponse, Long userId, String paymentKey) {
		Orders order = orderRepository.findByOrderNumber(orderNumber)
			.orElseThrow(() -> new NoSuchElementException("해당 주문이 존재하지 않습니다."));

		order.setOrderStatus(OrderStatus.PAID);
		order.setPaymentType(PaymentType.fromTossMethod(tossResponse.path("method").asText()));
		order.setPaymentKey(paymentKey);

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
			Product product = orderProduct.getProduct();
			productRepository.decreaseStock(product.getId(), orderProduct.getQuantity());
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
