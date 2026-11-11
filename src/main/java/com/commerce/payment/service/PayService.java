package com.commerce.payment.service;

import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.PaymentType;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.exception.ApiException;
import com.commerce.order.domain.Orders;
import com.commerce.order.service.OrderService;
import com.commerce.payment.domain.Payment;
import com.commerce.payment.dto.CancelResponseDTO;
import com.commerce.payment.repository.PaymentRepository;
import com.commerce.payment.dto.PayConfirmDTO;
import com.commerce.payment.dto.PaySuccessDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayService {

	// 사용자가 기다리는 중이라, 응답을 모르면 그 자리에서 같은 멱등키로 1번만 다시 요청한다. 그래도 모르면 202 확인 중
	private static final int CONFIRM_MAX_RETRIES = 1;
	// 취소도 같다. 그래도 모르면 취소 처리 중(CANCEL_REQUESTED)
	private static final int CANCEL_MAX_RETRIES = 1;

	private final OrderService orderService;
	private final PaymentTxService paymentTxService;
	private final PaymentApprovalService paymentApprovalService;
	private final PaymentCancelService paymentCancelService;
	private final PaymentRepository paymentRepository;

	public CancelResponseDTO cancel(String orderNumber, Long userId) {
		Orders order = orderService.findMyOrder(orderNumber, userId);

		// 1. 주문 잠금 + 상태 전이 (→ CANCEL_REQUESTED) - 동시 취소 요청 방지
		String paymentKey = paymentTxService.beginCancel(order.getId());

		// paymentKey 없으면 결제를 하지 않은 주문 — 바로 취소
		if (paymentKey == null || paymentKey.isEmpty()) {
			paymentTxService.applyCancelSuccess(order.getId(), false);
			return new CancelResponseDTO(true, orderNumber, LocalDateTime.now(), 0, "결제 안함", null, OrderStatus.CANCELED);
		}

		// 2. 토스 취소 요청 + 결과 반영. 응답을 모르면 같은 멱등키로 1번 다시 요청한다
		OrderStatus status = paymentCancelService.cancel(order, paymentKey, CANCEL_MAX_RETRIES);
		if (status == OrderStatus.CANCEL_REQUESTED) {
			// 결과를 모름. 보정 스케줄러가 같은 키로 다시 요청해 끝낸다
			return new CancelResponseDTO(false, orderNumber, null, 0, null, null, OrderStatus.CANCEL_REQUESTED);
		}

		// 3. 환불 금액·수단은 승인 때 저장한 값이다. 전액 취소라 결제 금액이 곧 환불 금액이고,
		//    토스가 '이미 취소됨'으로 답하면 응답에 결제 객체가 없다
		Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
		return new CancelResponseDTO(true, orderNumber, LocalDateTime.now(), payment.getAmount(),
			PaymentType.textOf(payment.getPaymentType()), null, OrderStatus.CANCELED);
	}

	// 확정 후 주문 상태(PAID / PAYMENT_PENDING / CANCELED)를 돌려준다
	public OrderStatus confirm(PayConfirmDTO req, Long userId) {
		log.info("confirm start: orderId={}, paymentKey={}, amount={}",
			req.getOrderId(), req.getPaymentKey(), req.getAmount());

		// 1. 주문 조회 (본인 주문만)
		Orders order = orderService.findMyOrder(req.getOrderId(), userId);

		// 2. 같은 paymentKey로 이미 확정을 시작한 주문(새로고침·재전송)이면 토스를 다시 부르지 않고 지금 상태를 돌려준다
		if (isAlreadyRequested(order, req)) {
			log.info("이미 확정을 시작한 주문, 지금 상태 반환: orderNumber={}, status={}", order.getOrderNumber(), order.getOrderStatus());
			return order.getOrderStatus();
		}

		// 3. 검증
		validatePayment(order, req);

		// 4. 재고 차감 (토스 호출 전)
		paymentTxService.lockAndDeductStock(req.getOrderId(), req.getPaymentKey());

		// 5. 토스 승인 요청 + 결과 반영. 응답을 모르면 같은 멱등키로 1번 다시 요청한다
		return paymentApprovalService.approve(req, CONFIRM_MAX_RETRIES).status();
	}

	// 결제 결과(결제 완료 화면, 확정 응답, 결제 대기 화면 폴링)
	public PaySuccessDTO getPaymentResult(String orderNumber, Long userId) {
		Orders order = orderService.findMyOrder(orderNumber, userId);
		Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
		return PaySuccessDTO.from(order, payment);
	}

	private boolean isAlreadyRequested(Orders order, PayConfirmDTO req) {
		OrderStatus status = order.getOrderStatus();
		if (status != OrderStatus.PAID && status != OrderStatus.PAYMENT_PENDING) {
			return false;
		}
		return paymentRepository.findByOrderId(order.getId())
			.map(payment -> Objects.equals(payment.getPaymentKey(), req.getPaymentKey()))
			.orElse(false);
	}

	private void validatePayment(Orders order, PayConfirmDTO req) {
		log.info("order loaded: orderNumber={}, status={}, finalPrice={}",
			order.getOrderNumber(), order.getOrderStatus(), order.getFinalPrice());

		// 1. 상태 확인
		if (order.getOrderStatus() != OrderStatus.READY) {
			log.warn("결제 가능한 상태가 아닙니다. status={}", order.getOrderStatus());
			throw new ApiException(GeneralResponseCode.PAYMENT_NOT_PAYABLE);
		}

		// 2. 금액 검증
		if (order.getFinalPrice() != req.getAmount()) {
			log.info("결제 금액이 일치하지 않습니다. orderFinalPrice: {}, reqAmount: {}", order.getFinalPrice(), req.getAmount());
			throw new ApiException(GeneralResponseCode.PAYMENT_AMOUNT_INVALID);
		}
	}

}
