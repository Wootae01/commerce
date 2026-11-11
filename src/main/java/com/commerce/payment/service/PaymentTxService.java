package com.commerce.payment.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.exception.ApiException;
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
import com.commerce.payment.domain.Payment;
import com.commerce.payment.repository.PaymentRepository;
import com.commerce.product.repository.ProductJdbcRepository;
import com.commerce.payment.external.TossPaymentResponse;

import com.commerce.user.domain.User;
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
	private final PaymentRepository paymentRepository;

	// 재고 수정. 재고는 옵션에 있다.
	@Transactional
	public void updateStock(Long orderId, boolean isIncrease) {

		List<OrderProduct> orderProducts = orderProductRepository.findOrderProductByOrderIdWithProduct(
			orderId);
		Map<Long, Integer> qtyByOptionId = orderProducts.stream()
			.collect(Collectors.groupingBy(
				op -> op.getProductOption().getId(),
				Collectors.summingInt(OrderProduct::getQuantity)
			));

		productJdbcRepository.updateOptionStock(qtyByOptionId, isIncrease);
	}

	// 취소 시작: 주문 잠금 + CANCEL_REQUESTED 전이 (동시 취소 요청 방지)
	@Transactional
	public String beginCancel(Long orderId) {
		Orders order = orderRepository.findByIdWithLock(orderId)
			.orElseThrow(() -> new ApiException(GeneralResponseCode.ORDER_NOT_FOUND));

		order.requestCancel();
		// 결제를 시도하지 않은 주문(READY)에는 결제가 없다
		return paymentRepository.findByOrderId(orderId)
			.map(Payment::getPaymentKey)
			.orElse(null);
	}

	// 취소 완료: 상태 변경 + 선택적 재고 복원
	@Transactional
	public void applyCancelSuccess(Long orderId, boolean restoreStock) {
		Orders order = orderRepository.findByIdWithLock(orderId)
			.orElseThrow(() -> new ApiException(GeneralResponseCode.ORDER_NOT_FOUND));

		order.completeCancel();
		if (restoreStock) {
			updateStock(orderId, true);
		}
	}

	// 토스 취소 실패 시 상태 복원
	@Transactional
	public void revertCancelRequest(Long orderId) {
		Orders order = orderRepository.findByIdWithLock(orderId)
			.orElseThrow(() -> new ApiException(GeneralResponseCode.ORDER_NOT_FOUND));

		order.revertCancel();
	}

	// 결제 성공 시 장바구니 삭제, 결제 일시, 상태 변경. 반영 후 주문 상태를 돌려준다
	@Transactional
	public OrderStatus applyPaymentSuccess(String orderNumber, TossPaymentResponse tossResponse) {
		Orders order = orderRepository.findByOrderNumberWithLock(orderNumber)
			.orElseThrow(() -> new ApiException(GeneralResponseCode.ORDER_NOT_FOUND));

		// 결제 수단
		PaymentType paymentType = PaymentType.fromTossMethod(tossResponse.method());

		// 결제 일시
		String approvedAtStr = tossResponse.approvedAt();

		LocalDateTime approvedAt = null;
		if (approvedAtStr != null && !approvedAtStr.isBlank()) {
			try {
				approvedAt = OffsetDateTime.parse(approvedAtStr).toLocalDateTime();
			} catch (DateTimeParseException e) {
				log.warn("Invalid approvedAt from Toss. value={}", approvedAtStr, e);
				approvedAt =LocalDateTime.now();
			}

		}

		// 이미 처리된 주문(즉시 확정·보정 스케줄러 중 먼저 반영된 쪽이 있음)이면 아무것도 하지 않는다
		if (!order.markPaid()) {
			log.warn("이미 처리된 주문입니다. orderNumber={}, status={}", orderNumber, order.getOrderStatus());
			return order.getOrderStatus();
		}
		Payment payment = paymentRepository.findByOrderId(order.getId())
			.orElseThrow(() -> new IllegalStateException("결제 진행 중인 주문에 결제 정보가 없습니다. orderNumber=" + orderNumber));
		payment.approve(paymentType, approvedAt);

		// 장바구니 삭제 - 보정 스케줄러에는 로그인 정보가 없으므로 주문자 기준으로 지운다
		if (order.getOrderType() == OrderType.CART) {
			List<Long> ids = order.getCartProductIds();
			if (!ids.isEmpty()) {
				User orderer = order.getUser();
				cartProductRepository.deleteSelectedFromUserCart(ids, orderer.getId(), orderer.getUsername());
			}
		}
		return order.getOrderStatus();
	}

	@Transactional
	public void lockAndDeductStock(String orderNumber, String paymentKey) {
		Orders order = orderRepository.findByOrderNumberWithLock(orderNumber)
			.orElseThrow(() -> new ApiException(GeneralResponseCode.ORDER_NOT_FOUND));

		order.startPayment();
		paymentRepository.save(Payment.start(order, paymentKey));
		updateStock(order.getId(), false);
	}

	// 토스가 실패를 알려 주면 재고 복원 + 취소 + 실패 이유 기록. 반영 후 주문 상태를 돌려준다
	// 이미 처리된 주문이면 먼저 반영한 쪽의 실패 이유를 그대로 둔다
	@Transactional
	public OrderStatus applyPaymentFailure(String orderNumber, FailureReason reason) {
		Orders order = orderRepository.findByOrderNumberWithLock(orderNumber)
			.orElseThrow(() -> new ApiException(GeneralResponseCode.ORDER_NOT_FOUND));

		if (!order.cancelPending()) {
			log.warn("이미 처리된 주문입니다. orderNumber={}, status={}", orderNumber, order.getOrderStatus());
			return order.getOrderStatus();
		}

		Payment payment = paymentRepository.findByOrderId(order.getId())
			.orElseThrow(() -> new IllegalStateException("결제 진행 중인 주문에 결제 정보가 없습니다. orderNumber=" + orderNumber));
		payment.fail(reason.code(), reason.message());
		updateStock(order.getId(), true);
		return order.getOrderStatus();
	}
}
