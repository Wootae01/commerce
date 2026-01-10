package com.commerce.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commerce.domain.OrderProduct;
import com.commerce.domain.Orders;
import com.commerce.domain.Product;
import com.commerce.domain.enums.OrderStatus;
import com.commerce.domain.enums.OrderType;
import com.commerce.domain.enums.PaymentType;
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

	// 주문 상태 변경, 재고 수정
	@Transactional
	public void updateOrderStatusAndStock(Orders order, OrderStatus status, boolean isIncrease) {

		// 주문 상태 변경
		order.setOrderStatus(status);

		// 재고 수정
		List<OrderProduct> orderProducts = order.getOrderProducts();
		for (OrderProduct orderProduct : orderProducts) {
			Product product = orderProduct.getProduct();

			if (isIncrease) {
				productRepository.increaseStock(product.getId(), orderProduct.getQuantity());
			} else {
				productRepository.decreaseStock(product.getId(), orderProduct.getQuantity());
			}

		}
	}

	// 결제 성공 시 재고 차감, 장바구니 삭제, 결제 일시, 상태 변경
	@Transactional
	public void applyPaymentSuccess(String orderNumber, JsonNode tossResponse, Long userId, String paymentKey) {
		Orders order = orderRepository.findByOrderNumber(orderNumber)
			.orElseThrow(() -> new NoSuchElementException("해당 주문이 존재하지 않습니다."));

		// 결제 수단, paymentKey
		order.setPaymentType(PaymentType.fromTossMethod(tossResponse.path("method").asText()));
		order.setPaymentKey(paymentKey);

		// 결제 일시
		String approvedAtStr = tossResponse.path("approvedAt").asText();

		LocalDateTime approvedAt = null;
		if (approvedAtStr != null && !approvedAtStr.isBlank()) {
			approvedAt = OffsetDateTime.parse(approvedAtStr).toLocalDateTime();
		}

		order.setApprovedAt(approvedAt);

		// 재고 차감, 주문 상태 변경
		updateOrderStatusAndStock(order, OrderStatus.PAID, false);


		// 장바구니 삭제
		if (order.getOrderType() == OrderType.CART) {
			List<Long> ids = order.getCartProductIds();
			if (!ids.isEmpty()) {
				cartProductRepository.deleteSelectedFromUserCart(ids, userId);
			}
		}
	}
}
