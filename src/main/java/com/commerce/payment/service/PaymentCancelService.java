package com.commerce.payment.service;

import org.springframework.stereotype.Service;

import com.commerce.common.enums.OrderStatus;
import com.commerce.order.domain.Orders;
import com.commerce.payment.external.TossApiException;
import com.commerce.payment.external.TossConnectionException;
import com.commerce.payment.external.TossPaymentClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 멱등키(주문번호)로 토스에 주문 취소를 요청하고, 결과를 분류해 주문에 반영한다.
 *
 * <pre>
 * 취소 요청 결과                                         처리
 * 200, ALREADY_CANCELED_PAYMENT·ALREADY_REFUND_PAYMENT   CANCELED + 재고 복원
 * 409 IDEMPOTENT_REQUEST_PROCESSING, 401·429·5xx,
 * 네트워크 오류, 응답 해석 실패                          CANCEL_REQUESTED 유지(취소 처리 중)
 * 그 외 4xx(거절·불가)                                   PAID로 되돌림
 * </pre>
 * 네트워크 오류·5xx만 그 자리에서 같은 키로 다시 요청한다(승인과 같음).
 * 결과를 모르면 결제완료로 되돌리지 않는다. 사용자가 이미 취소하기로 했으므로 같은 키로 다시 요청하는 게 항상 맞다.
 * 토스에서 이미 취소됐으면 같은 응답이 오고, 요청이 도착하지 않았으면 그때 취소된다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCancelService {

	// 사용자에게 사유를 받지 않는다. 보정 스케줄러가 다시 요청할 때도 같은 본문을 보낸다
	static final String CANCEL_REASON = "단순 변심";

	private final TossPaymentClient tossPaymentClient;
	private final PaymentTxService paymentTxService;

	// CANCEL_REQUESTED 주문의 취소를 요청하고 반영한 뒤 주문 상태를 돌려준다
	// 응답을 모르면 같은 키로 최대 maxRetries번 다시 요청한다. 토스가 거절하면 PAID로 되돌리고 TossApiException을 던진다
	public CancelResult cancel(Orders order, String paymentKey, int maxRetries) {
		try {
			requestWithRetry(order, paymentKey, maxRetries);
		} catch (TossApiException e) {
			if (isResultUnknown(e)) {
				return keepCancelRequested(order, e);
			}
			if (!e.isAlreadyCanceled()) {
				// 토스가 거절·불가를 명시적으로 알려 줌
				revert(order, e);
				throw e;
			}
			log.info("토스에서 이미 취소된 결제, 취소 완료로 반영: orderNumber={}", order.getOrderNumber());
		} catch (Exception e) {
			// 네트워크 오류, 응답 해석 실패 등. 토스에서는 환불됐을 수 있으므로 결제완료로 되돌리지 않는다
			return keepCancelRequested(order, e);
		}

		return new CancelResult(paymentTxService.applyCancelSuccess(order.getId(), true), false);
	}

	private CancelResult keepCancelRequested(Orders order, Exception e) {
		log.warn("취소 결과 모름, 취소 처리 중 유지: orderNumber={}", order.getOrderNumber(), e);
		return new CancelResult(OrderStatus.CANCEL_REQUESTED, isTossUnreachable(e));
	}

	// 취소 여부를 알 수 없는 오류 응답. 401·429·5xx(승인과 같음)와, 같은 키의 이전 요청을 토스가 처리 중(409)
	private boolean isResultUnknown(TossApiException e) {
		return e.isResultUnknown() || e.isIdempotentRequestProcessing();
	}

	// 토스와 통신하지 못함(네트워크 오류·타임아웃·5xx). 같은 키로 다시 요청할 수 있다
	private boolean isTossUnreachable(Exception e) {
		return e instanceof TossConnectionException
			|| (e instanceof TossApiException tossError && tossError.isServerError());
	}

	// 토스는 같은 멱등키의 취소를 한 번만 처리하고 같은 응답을 돌려주므로, 다시 요청해도 두 번 환불되지 않는다
	private void requestWithRetry(Orders order, String paymentKey, int maxRetries) {
		for (int retry = 0; ; retry++) {
			try {
				tossPaymentClient.cancel(paymentKey, order.getOrderNumber(), CANCEL_REASON);
				return;
			} catch (TossConnectionException | TossApiException e) {
				if (!isTossUnreachable(e) || retry >= maxRetries) {
					throw e;
				}
				log.warn("취소 응답 모름, 같은 멱등키로 재요청({}/{}): orderNumber={}, cause={}",
					retry + 1, maxRetries, order.getOrderNumber(), e.toString());
			}
		}
	}

	private void revert(Orders order, Exception e) {
		log.error("토스 취소 실패, 주문 상태 복원: orderNumber={}", order.getOrderNumber(), e);
		paymentTxService.revertCancelRequest(order.getId());
	}
}
