package com.commerce.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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
			.orElseThrow(() -> new NoSuchElementException("해당 주문이 존재하지 않습니다."));

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
			.orElseThrow(() -> new NoSuchElementException("해당 주문이 존재하지 않습니다."));

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
			.orElseThrow(() -> new NoSuchElementException("해당 주문이 존재하지 않습니다."));

		if (order.getOrderStatus() == OrderStatus.CANCEL_REQUESTED) {
			order.setOrderStatus(OrderStatus.PAID);
		}
	}

	// 결제 성공 시 재고 차감, 장바구니 삭제, 결제 일시, 상태 변경
	@Transactional
	public void applyPaymentSuccess(String orderNumber, JsonNode tossResponse, Long userId, String paymentKey) {
		Orders order = orderRepository.findByOrderNumberWithLock(orderNumber)
			.orElseThrow(() -> new NoSuchElementException("해당 주문이 존재하지 않습니다."));

		// 멱등성 보장: 이미 처리된 주문이면 중복 처리 방지
		if (order.getOrderStatus() != OrderStatus.READY) {
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
