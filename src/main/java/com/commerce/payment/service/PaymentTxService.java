package com.commerce.payment.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import com.commerce.common.exception.EntityNotFoundException;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commerce.order.domain.OrderProduct;
import com.commerce.order.domain.Orders;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.OrderType;
import com.commerce.common.enums.PaymentType;
import com.commerce.cart.repository.CartProductRepository;
import com.commerce.order.repository.OrderProductRepository;
import com.commerce.order.repository.OrderRepository;
import com.commerce.product.repository.ProductJdbcRepository;
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

	// 재고 수정
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

	// 취소 시작: 주문 잠금 + CANCEL_REQUESTED 전이 (동시 취소 요청 방지)
	@Transactional
	public String beginCancel(Long orderId) {
		Orders order = orderRepository.findByIdWithLock(orderId)
			.orElseThrow(() -> new EntityNotFoundException("해당 주문이 존재하지 않습니다."));

		OrderStatus status = order.getOrderStatus();
		if (!(status == OrderStatus.PAID || status == OrderStatus.READY)) {
			throw new IllegalStateException("현재 상태에서는 주문 취소를할 수 없습니다.");
		}

		order.setOrderStatus(OrderStatus.CANCEL_REQUESTED);
		return order.getPaymentKey();
	}

	// 취소 완료: 상태 변경 + 선택적 재고 복원
	@Transactional
	public void applyCancelSuccess(Long orderId, boolean restoreStock) {
		Orders order = orderRepository.findByIdWithLock(orderId)
			.orElseThrow(() -> new EntityNotFoundException("해당 주문이 존재하지 않습니다."));

		if (order.getOrderStatus() != OrderStatus.CANCEL_REQUESTED) {
			log.warn("취소 처리할 수 없는 상태입니다. orderId={}, status={}", orderId, order.getOrderStatus());
			throw new IllegalStateException("취소 처리할 수 없는 상태입니다.");
		}

		order.setOrderStatus(OrderStatus.CANCELED);
		if (restoreStock) {
			updateStock(orderId, true);
		}
	}

	// 토스 취소 실패 시 상태 복원
	@Transactional
	public void revertCancelRequest(Long orderId) {
		Orders order = orderRepository.findByIdWithLock(orderId)
			.orElseThrow(() -> new EntityNotFoundException("해당 주문이 존재하지 않습니다."));

		if (order.getOrderStatus() == OrderStatus.CANCEL_REQUESTED) {
			order.setOrderStatus(OrderStatus.PAID);
		}
	}

	// 결제 성공 시 재고 차감, 장바구니 삭제, 결제 일시, 상태 변경
	@Transactional
	public void applyPaymentSuccess(String orderNumber, JsonNode tossResponse, Long userId, String paymentKey) {
		Orders order = orderRepository.findByOrderNumber(orderNumber)
			.orElseThrow(() -> new EntityNotFoundException("해당 주문이 존재하지 않습니다."));

		if (order.getOrderStatus() != OrderStatus.PAYMENT_PENDING) {
			log.warn("이미 처리된 주문입니다. orderNumber={}, status={}", orderNumber, order.getOrderStatus());
			throw new IllegalStateException("이미 처리된 주문입니다.");
		}

		// 결제 수단, paymentKey
		order.setPaymentType(PaymentType.fromTossMethod(tossResponse.path("method").asText()));
		order.setPaymentKey(paymentKey);

		// 결제 일시
		String approvedAtStr = tossResponse.path("approvedAt").asText();

		LocalDateTime approvedAt = null;
		if (approvedAtStr != null && !approvedAtStr.isBlank()) {
			try {
				approvedAt = OffsetDateTime.parse(approvedAtStr).toLocalDateTime();
			} catch (DateTimeParseException e) {
				log.warn("Invalid approvedAt from Toss. value={}", approvedAtStr, e);
				approvedAt =LocalDateTime.now();
			}

		}

		order.setApprovedAt(approvedAt);

		// 주문 상태 변경
		order.setOrderStatus(OrderStatus.PAID);


		// 장바구니 삭제
		if (order.getOrderType() == OrderType.CART) {
			List<Long> ids = order.getCartProductIds();
			if (!ids.isEmpty()) {
				cartProductRepository.deleteSelectedFromUserCart(ids, userId);
			}
		}
	}

	@Transactional
	public void lockAndDeductStock(String orderNumber) {
		Orders order = orderRepository.findByOrderNumberWithLock(orderNumber)
			.orElseThrow(() -> new EntityNotFoundException("해당 주문이 존재하지 않습니다."));

		if (order.getOrderStatus() != OrderStatus.READY) {
			throw new IllegalStateException("이미 처리된 주문입니다.");
		}

		order.setOrderStatus(OrderStatus.PAYMENT_PENDING);
		updateStock(order.getId(), false);
	}

	@Transactional
	public void restoreStockOnTossFailure(String orderNumber) {
		Orders order = orderRepository.findByOrderNumber(orderNumber)
			.orElseThrow(() -> new EntityNotFoundException("해당 주문이 존재하지 않습니다."));
		updateStock(order.getId(), true);
		order.setOrderStatus(OrderStatus.CANCELED);
	}
}
