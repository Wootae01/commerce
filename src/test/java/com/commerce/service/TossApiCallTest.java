package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.commerce.common.code.ExternalResponseCode;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.exception.ApiException;
import com.commerce.order.domain.Orders;
import com.commerce.payment.dto.CancelResponseDTO;
import com.commerce.payment.dto.PayConfirmDTO;
import com.github.tomakehurst.wiremock.http.Fault;

/**
 * 토스로 보내는 요청의 모양(경로·인증 헤더·본문)과 주문 취소.
 * HTTP 클라이언트를 바꿔도 토스가 받는 요청과 취소 결과가 같아야 한다.
 */
class TossApiCallTest extends PaymentIntegrationTestSupport {

	// 시크릿 키 뒤에 ':'을 붙여 Base64로 인코딩한 값(토스 Basic 인증)
	private static final String TOSS_BASIC_AUTH = "Basic dGVzdF9za19jb21tZXJjZTo=";

	@BeforeEach
	void stubConfirmApproved() {
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody(tossPayment("DONE", "카드", 0))));
	}

	@Test
	@DisplayName("승인 요청은 시크릿 키 Basic 인증으로 paymentKey·주문번호·금액을 JSON으로 보낸다")
	void confirm_sendsAuthAndJsonBody() {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO("pk-" + UUID.randomUUID(), order.getOrderNumber(), order.getFinalPrice());

		// when
		payService.confirm(req, userId);

		// then
		verify(1, postRequestedFor(urlEqualTo("/v1/payments/confirm"))
			.withHeader("Authorization", equalTo(TOSS_BASIC_AUTH))
			.withHeader("Content-Type", containing("application/json"))
			.withRequestBody(equalToJson("""
				{ "paymentKey": "%s", "orderId": "%s", "amount": %d }
				""".formatted(req.getPaymentKey(), order.getOrderNumber(), order.getFinalPrice()))));
	}

	@Test
	@DisplayName("승인 결과를 몰라 결제를 조회할 때도 시크릿 키 Basic 인증을 붙여 paymentKey로 조회한다")
	void getPayment_sendsAuth() {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO("pk-" + UUID.randomUUID(), order.getOrderNumber(), order.getFinalPrice());
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
		stubFor(get(urlEqualTo("/v1/payments/" + req.getPaymentKey()))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody(tossPayment("DONE", "카드", 0))));

		// when
		OrderStatus result = payService.confirm(req, userId);

		// then
		assertThat(result).isEqualTo(OrderStatus.PAID);
		verify(1, getRequestedFor(urlEqualTo("/v1/payments/" + req.getPaymentKey()))
			.withHeader("Authorization", equalTo(TOSS_BASIC_AUTH)));
	}

	@Test
	@DisplayName("결제한 주문을 취소하면 시크릿 키 Basic 인증과 멱등키로 취소 이유를 보내고, 결제 금액·승인 때 결제 수단으로 응답하며 재고를 복원한다")
	void cancel_paidOrder_canceledWithPaymentInfo() {
		// given
		Orders order = paidOrder();
		String paymentKey = paymentKeyOf(order);
		stubFor(post(urlEqualTo("/v1/payments/" + paymentKey + "/cancel"))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody(tossPayment("CANCELED", "간편결제", 3500))));

		// when
		CancelResponseDTO response = payService.cancel(order.getOrderNumber(), userId);

		// then
		assertThat(response.isSuccess()).isTrue();
		// 토스 응답(3500·간편결제)이 아니라 승인 때 저장한 값이다
		assertThat(response.getCancelAmount()).as("환불 금액 = 결제 금액").isEqualTo(order.getFinalPrice());
		assertThat(response.getRefundMethod()).as("승인 때 결제 수단").isEqualTo("카드");
		assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELED);
		assertThat(stock()).as("재고").isEqualTo(INITIAL_STOCK);
		verify(1, postRequestedFor(urlEqualTo("/v1/payments/" + paymentKey + "/cancel"))
			.withHeader("Authorization", equalTo(TOSS_BASIC_AUTH))
			// 응답을 모를 때 같은 키로 다시 요청해도 한 번만 취소된다
			.withHeader("Idempotency-Key", equalTo(order.getOrderNumber()))
			.withHeader("Content-Type", containing("application/json"))
			.withRequestBody(equalToJson("""
				{ "cancelReason": "단순 변심" }
				""")));
	}

	@Test
	@DisplayName("토스가 이미 취소된 결제라고 하면 취소 완료로 보고 재고를 복원하며, 환불 금액·수단은 결제 정보로 응답한다")
	void cancel_alreadyCanceled_canceledWithPaymentInfo() {
		// given - 토스 관리자 화면에서 먼저 취소했거나, 멱등키가 만료된 뒤 다시 요청한 경우
		Orders order = paidOrder();
		stubCancel(order, 400, """
			{ "code": "ALREADY_CANCELED_PAYMENT", "message": "이미 취소된 결제 입니다." }
			""");

		// when
		CancelResponseDTO response = payService.cancel(order.getOrderNumber(), userId);

		// then - 응답에 결제 객체가 없으므로 금액·수단은 승인 때 저장한 값이다(전액 취소)
		assertThat(response.isSuccess()).isTrue();
		assertThat(response.getCancelAmount()).as("환불 금액 = 결제 금액").isEqualTo(order.getFinalPrice());
		assertThat(response.getRefundMethod()).as("승인 때 결제 수단").isEqualTo("카드");
		assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELED);
		assertThat(stock()).as("재고").isEqualTo(INITIAL_STOCK);
	}

	@Test
	@DisplayName("토스가 취소를 거절하면 취소 실패 예외를 던지고, 주문은 결제 완료로 돌아오며 재고는 그대로다")
	void cancel_tossRejects_orderRevertedToPaid() {
		// given
		Orders order = paidOrder();
		stubCancel(order, 403, """
			{ "code": "NOT_CANCELABLE_PAYMENT", "message": "취소 할 수 없는 결제 입니다." }
			""");

		// when & then
		assertThatThrownBy(() -> payService.cancel(order.getOrderNumber(), userId))
			.isInstanceOfSatisfying(ApiException.class, e ->
				assertThat(e.getResponseCode()).isEqualTo(ExternalResponseCode.PG_CANCEL_ERROR));
		assertThat(statusOf(order)).isEqualTo(OrderStatus.PAID);
		assertThat(stock()).as("재고").isEqualTo(INITIAL_STOCK - 1);
	}

	// 토스 Payment 객체. 우리가 읽지 않는 필드도 실제 응답처럼 넣는다
	private static String tossPayment(String status, String method, int cancelAmount) {
		String cancels = cancelAmount == 0 ? "null" : """
			[ { "transactionKey": "tx-1", "cancelReason": "단순 변심", "canceledAt": "2026-01-04T10:00:00+09:00",
			    "cancelAmount": %d, "taxFreeAmount": 0, "refundableAmount": 0, "cancelStatus": "DONE" } ]
			""".formatted(cancelAmount);
		return """
			{
			  "mId": "tosspayments", "version": "2022-11-16", "paymentKey": "pk", "orderId": "order",
			  "orderName": "상품1", "currency": "KRW", "status": "%s", "method": "%s",
			  "requestedAt": "2026-01-03T01:23:00+09:00", "approvedAt": "2026-01-03T01:23:45+09:00",
			  "totalAmount": 3500, "balanceAmount": 0, "suppliedAmount": 3182, "vat": 318,
			  "card": { "issuerCode": "61", "number": "43301234****123*", "approveNo": "00000000" },
			  "easyPay": null, "receipt": { "url": "https://dashboard.tosspayments.com/receipt" },
			  "failure": null, "cancels": %s
			}
			""".formatted(status, method, cancels);
	}
}
