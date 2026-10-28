package com.commerce.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.commerce.common.enums.OrderStatus;
import com.commerce.payment.dto.PayConfirmDTO;
import com.commerce.payment.external.TossApiException;
import com.commerce.payment.external.TossPaymentClient;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 멱등키(주문번호)로 토스에 승인을 요청하고, 결과를 분류해 주문에 반영한다.
 * 확정 요청(응답을 모르면 1번 재요청)과 보정 스케줄러가 같은 분류를 쓴다.
 *
 * <pre>
 * 승인 요청 결과                      처리
 * 200                                 PAID
 * 409 IDEMPOTENT_REQUEST_PROCESSING   PENDING 유지
 * 401·429·5xx·네트워크 오류           결제 조회 → DONE: PAID / ABORTED·EXPIRED·CANCELED: 취소
 *                                     / READY·IN_PROGRESS: PENDING / 그 외: PENDING + 에러 로그 / 조회 실패: PENDING
 * 그 외 4xx(거절·만료 등)             재고 복원 + CANCELED
 * </pre>
 * 주문은 토스가 승인·실패를 확실히 알려 줄 때만 바꾸고, 결과를 모르면 재고를 차감한 채 PAYMENT_PENDING으로 둔다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentApprovalService {

	private final TossPaymentClient tossPaymentClient;
	private final PaymentTxService paymentTxService;

	// PAYMENT_PENDING 주문의 승인을 요청하고 반영한 뒤 주문 상태를 돌려준다(다른 요청이 먼저 반영했으면 그 상태). 응답을 모르면(끊김·타임아웃·5xx) 같은 키로 최대 maxRetries번 다시 요청한다
	public ApprovalResult approve(PayConfirmDTO req, int maxRetries) {
		long startTime = System.currentTimeMillis();
		JsonNode tossResponse;
		try {
			tossResponse = requestWithRetry(req, maxRetries);
		} catch (TossApiException e) {
			if (e.isIdempotentRequestProcessing()) {
				log.warn("같은 멱등키의 승인을 토스가 처리 중, PENDING 유지: orderId={}", req.getOrderId());
				return new ApprovalResult(OrderStatus.PAYMENT_PENDING, false);
			}
			if (e.isResultUnknown()) {
				return new ApprovalResult(checkByPaymentQuery(req), isRetryable(e));
			}
			// 토스가 거절·만료 등을 명시적으로 알려 줌
			return new ApprovalResult(paymentTxService.applyPaymentFailure(req.getOrderId()), false);
		} catch (Exception e) {
			// 네트워크 오류, 응답 해석 실패 등. 토스에서는 승인됐을 수 있으므로 취소하지 않는다
			log.warn("승인 결과 모름: orderId={}", req.getOrderId(), e);
			return new ApprovalResult(checkByPaymentQuery(req), e instanceof WebClientRequestException);
		}
		log.info("request toss duration in confirm: {}", System.currentTimeMillis() - startTime);

		// 장바구니 삭제, 결제 일시, 상태 변경
		return new ApprovalResult(paymentTxService.applyPaymentSuccess(req.getOrderId(), tossResponse, req.getPaymentKey()), false);
	}

	// 토스는 같은 멱등키의 승인을 한 번만 처리하고 같은 응답을 돌려주므로, 다시 요청해도 이중 승인이 생기지 않는다
	// 재요청할 수 없는 실패이거나 재요청을 다 쓰면 예외를 그대로 던져 approve가 분류한다
	private JsonNode requestWithRetry(PayConfirmDTO req, int maxRetries) {
		for (int retry = 0; ; retry++) {
			try {
				return tossPaymentClient.confirm(req);
			} catch (WebClientRequestException | TossApiException e) {
				if (!isRetryable(e) || retry >= maxRetries) {
					throw e;
				}
				log.warn("승인 응답 모름, 같은 멱등키로 재요청({}/{}): orderId={}, cause={}",
					retry + 1, maxRetries, req.getOrderId(), e.toString());
			}
		}
	}

	private boolean isRetryable(RuntimeException e) {
		return e instanceof WebClientRequestException
			|| (e instanceof TossApiException tossError && tossError.isServerError());
	}

	// 토스 결제 조회로 결과를 확인한다
	private OrderStatus checkByPaymentQuery(PayConfirmDTO req) {
		log.warn("승인 결과 모름, Toss 결제 상태 조회: orderId={}", req.getOrderId());
		JsonNode payment;
		try {
			payment = tossPaymentClient.getPayment(req.getPaymentKey());
		} catch (Exception e) {
			log.warn("Toss 조회 실패, PENDING 유지: orderId={}", req.getOrderId(), e);
			return OrderStatus.PAYMENT_PENDING;
		}

		String status = payment.path("status").asText();
		switch (status) {
			case "DONE" -> {
				log.info("Toss 결제 확인됨, 승인 처리: orderId={}", req.getOrderId());
				return paymentTxService.applyPaymentSuccess(req.getOrderId(), payment, req.getPaymentKey());
			}
			case "ABORTED", "EXPIRED", "CANCELED" -> {
				log.info("Toss 결제 실패 확인, 재고 복원: orderId={}, status={}", req.getOrderId(), status);
				return paymentTxService.applyPaymentFailure(req.getOrderId());
			}
			case "READY", "IN_PROGRESS" -> {
				log.info("Toss 결제 처리 중, PENDING 유지: orderId={}, status={}", req.getOrderId(), status);
				return OrderStatus.PAYMENT_PENDING;
			}
			default -> {
				// WAITING_FOR_DEPOSIT(가상계좌, 미지원) 등. 사람이 확인해야 한다
				log.error("예상 밖 Toss 결제 상태, PENDING 유지: orderId={}, status={}", req.getOrderId(), status);
				return OrderStatus.PAYMENT_PENDING;
			}
		}
	}
}
