package com.commerce.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.commerce.domain.OrderProduct;
import com.commerce.domain.Orders;
import com.commerce.domain.enums.OrderStatus;
import com.commerce.domain.enums.OrderType;
import com.commerce.domain.enums.PaymentType;
import com.commerce.repository.CartProductRepository;
import com.commerce.repository.OrderProductRepository;
import com.commerce.repository.OrderRepository;
import com.commerce.repository.ProductJdbcRepository;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTxService {

	private final OrderRepository orderRepository;
	private final CartProductRepository cartProductRepository;
	private final OrderProductRepository orderProductRepository;
	private final ProductJdbcRepository productJdbcRepository;

	// 주문 상태 변경, 재고 수정
	@Transactional
	public void updateStock(Long orderId, boolean isIncrease) {

		List<OrderProduct> orderProducts = orderProductRepository.findOrderProductByOrderIdWithProduct(
			orderId);
		Map<Long, Integer> qtyByProductId = orderProducts.stream()
			.collect(Collectors.groupingBy(
				op -> op.getProduct().getId(),
				Collectors.summingInt(OrderProduct::getQuantity)
			));

		productJdbcRepository.updateStock(qtyByProductId, isIncrease);
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
		order.setOrderStatus(OrderStatus.PAID);
		updateStock(order.getId(), false);


		// 장바구니 삭제
		if (order.getOrderType() == OrderType.CART) {
			List<Long> ids = order.getCartProductIds();
			if (!ids.isEmpty()) {
				cartProductRepository.deleteSelectedFromUserCart(ids, userId);
			}
		}
	}

	@Transactional
	public void changeOrderStatus(Orders order, OrderStatus orderStatus) {
		order.setOrderStatus(orderStatus);
	}

	// 결제 보상 처리용 - 독립 트랜잭션으로 paymentKey와 상태를 저장
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void savePaymentKeyAndStatus(String orderNumber, String paymentKey, OrderStatus status) {
		Orders order = orderRepository.findByOrderNumber(orderNumber)
			.orElseThrow(() -> new NoSuchElementException("해당 주문이 존재하지 않습니다."));
		order.setPaymentKey(paymentKey);
		order.setOrderStatus(status);
	}
}
