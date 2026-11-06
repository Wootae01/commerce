package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.commerce.common.enums.OrderStatus;
import com.commerce.order.domain.Orders;
import com.commerce.order.dto.OrderDetailResponseDTO;
import com.commerce.payment.domain.Payment;
import com.commerce.payment.dto.PayConfirmDTO;
import com.commerce.payment.dto.PaySuccessDTO;
import com.commerce.user.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 결제 실패 이유(#37). 결제 실패로 주문이 취소되면 실패 이유(코드·문장)가 Payment에 남고,
 * 사용자에게는 문장만 보인다. 바로 거절, 결제 조회로 확정, 보정 스케줄러 모두 같은 규칙을 따른다.
 */
class PaymentFailureReasonTest extends PaymentIntegrationTestSupport {

	// MockMvc는 직접 만든다. @AutoConfigureMockMvc를 쓰면 컨텍스트가 하나 더 생겨 테스트 MySQL 커넥션 한도를 넘는다
	@Autowired WebApplicationContext context;
	@Autowired ObjectMapper objectMapper;

	@Test
	@DisplayName("토스가 승인을 거절(4xx)하면 토스의 code·message가 실패 이유로 남고, 결제 결과에 그 문장이 보인다")
	void rejected_tossCodeAndMessageSaved() {
		// given
		Orders order = prepareOrder();
		stubConfirm(403, """
			{ "code": "REJECT_CARD_COMPANY", "message": "카드사에서 승인을 거절했습니다." }
			""");

		// when
		OrderStatus status = payService.confirm(confirmRequestOf(order), userId);

		// then
		assertThat(status).isEqualTo(OrderStatus.CANCELED);
		PaySuccessDTO result = payService.getPaymentResult(order.getOrderNumber(), userId);
		assertThat(result.getFailureMessage()).isEqualTo("카드사에서 승인을 거절했습니다.");
		assertThat(paymentOf(order).getFailureCode()).isEqualTo("REJECT_CARD_COMPANY");
	}

	@Test
	@DisplayName("거절(4xx) 응답 본문을 해석할 수 없으면 코드는 비우고, 결제 결과에 기본 문장이 보인다")
	void rejected_unreadableBody_defaultMessage() {
		// given
		Orders order = prepareOrder();
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(400)
				.withHeader("Content-Type", "text/html")
				.withBody("<html>Bad Request</html>")));

		// when
		OrderStatus status = payService.confirm(confirmRequestOf(order), userId);

		// then
		assertThat(status).isEqualTo(OrderStatus.CANCELED);
		PaySuccessDTO result = payService.getPaymentResult(order.getOrderNumber(), userId);
		assertThat(result.getFailureMessage()).isEqualTo("결제 승인에 실패했습니다.");
		assertThat(paymentOf(order).getFailureCode()).isNull();
	}

	@Test
	@DisplayName("승인 결과를 몰라 결제를 조회했더니 중단(ABORTED)이고 실패 정보가 있으면, 그 code·message가 실패 이유로 남는다")
	void queryAborted_withFailure_tossFailureSaved() {
		// given - 승인 요청은 5xx(결과 모름), 조회하면 ABORTED
		Orders order = prepareOrder();
		PayConfirmDTO req = confirmRequestOf(order);
		stubConfirm(500, """
			{ "code": "FAILED_INTERNAL_SYSTEM_PROCESSING", "message": "내부 시스템 처리 작업이 실패했습니다." }
			""");
		stubQuery(req, """
			{ "status": "ABORTED",
			  "failure": { "code": "REJECT_CARD_PAYMENT", "message": "한도초과 혹은 잔액부족으로 결제에 실패했습니다." } }
			""");

		// when
		OrderStatus status = payService.confirm(req, userId);

		// then
		assertThat(status).isEqualTo(OrderStatus.CANCELED);
		PaySuccessDTO result = payService.getPaymentResult(order.getOrderNumber(), userId);
		assertThat(result.getFailureMessage()).isEqualTo("한도초과 혹은 잔액부족으로 결제에 실패했습니다.");
		assertThat(paymentOf(order).getFailureCode()).isEqualTo("REJECT_CARD_PAYMENT");
	}

	@Test
	@DisplayName("조회한 실패 정보에 code만 있고 message가 비어 있으면, 토스 code는 남기고 결제 결과에는 우리 문장이 보인다")
	void queryAborted_failureWithoutMessage_tossCodeKept() {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = confirmRequestOf(order);
		stubConfirm(500, """
			{ "code": "FAILED_INTERNAL_SYSTEM_PROCESSING", "message": "내부 시스템 처리 작업이 실패했습니다." }
			""");
		stubQuery(req, """
			{ "status": "ABORTED",
			  "failure": { "code": "REJECT_CARD_PAYMENT", "message": "" } }
			""");

		// when
		OrderStatus status = payService.confirm(req, userId);

		// then
		assertThat(status).isEqualTo(OrderStatus.CANCELED);
		PaySuccessDTO result = payService.getPaymentResult(order.getOrderNumber(), userId);
		assertThat(result.getFailureMessage()).isEqualTo("결제가 승인되지 않았습니다.");
		assertThat(paymentOf(order).getFailureCode()).isEqualTo("REJECT_CARD_PAYMENT");
	}

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
		"ABORTED, 결제가 승인되지 않았습니다.",
		"EXPIRED, 결제 시간이 만료되었습니다.",
		"CANCELED, 결제가 취소되었습니다."
	})
	@DisplayName("결제를 조회해 실패를 확정했는데 실패 정보가 없으면, 조회 상태를 코드로 남기고 결제 결과에 우리 문장이 보인다")
	void queryFailed_withoutFailure_statusAndOurMessage(String tossStatus, String expectedMessage) {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = confirmRequestOf(order);
		stubConfirm(500, """
			{ "code": "FAILED_INTERNAL_SYSTEM_PROCESSING", "message": "내부 시스템 처리 작업이 실패했습니다." }
			""");
		stubQuery(req, """
			{ "status": "%s" }
			""".formatted(tossStatus));

		// when
		OrderStatus status = payService.confirm(req, userId);

		// then
		assertThat(status).isEqualTo(OrderStatus.CANCELED);
		PaySuccessDTO result = payService.getPaymentResult(order.getOrderNumber(), userId);
		assertThat(result.getFailureMessage()).isEqualTo(expectedMessage);
		assertThat(paymentOf(order).getFailureCode()).isEqualTo(tossStatus);
	}

	@Test
	@DisplayName("보정 스케줄러가 다시 요청한 승인이 거절(4xx)되면, 주문 상세에 토스의 문장이 실패 이유로 보인다")
	void scheduler_rejected_orderDetailShowsFailureMessage() {
		// given - 결과를 모르는 채 2분 지난 주문
		Orders order = prepareOrder();
		stubConfirm(409, """
			{ "code": "IDEMPOTENT_REQUEST_PROCESSING", "message": "이전 멱등 요청이 처리중이에요." }
			""");
		payService.confirm(confirmRequestOf(order), userId);
		markUpdatedAt(order, LocalDateTime.now().minusMinutes(2));
		stubConfirm(403, """
			{ "code": "REJECT_CARD_COMPANY", "message": "카드사에서 승인을 거절했습니다." }
			""");

		// when
		paymentRecoveryScheduler.recoverPendingPayments();

		// then
		User user = userRepository.findById(userId).orElseThrow();
		OrderDetailResponseDTO detail = orderService.getMyOrderDetail(order.getOrderNumber(), user);
		assertThat(detail.getPaymentInfo().getFailureMessage()).isEqualTo("카드사에서 승인을 거절했습니다.");
		assertThat(paymentOf(order).getFailureCode()).isEqualTo("REJECT_CARD_COMPANY");
	}

	@ParameterizedTest
	@ValueSource(strings = {"/pay/confirm", "/api/payments/confirm"})
	@DisplayName("확정 요청이 거절되면 400과 실패 이유 문장으로 응답하고, 코드는 응답에 넣지 않는다")
	void confirmEndpoint_rejected_400WithMessage(String endpoint) throws Exception {
		// given
		Orders order = prepareOrder();
		stubConfirm(403, """
			{ "code": "REJECT_CARD_COMPANY", "message": "카드사에서 승인을 거절했습니다." }
			""");
		MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

		// when & then
		mockMvc.perform(MockMvcRequestBuilders.post(endpoint)
				.with(user("username").roles("USER"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(confirmRequestOf(order))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.message").value("카드사에서 승인을 거절했습니다."))
			.andExpect(jsonPath("$.failureCode").doesNotExist())
			.andExpect(jsonPath("$.code").doesNotExist());
	}

	private void stubQuery(PayConfirmDTO req, String body) {
		stubFor(get(urlEqualTo("/v1/payments/" + req.getPaymentKey()))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody(body)));
	}

	private Payment paymentOf(Orders order) {
		return paymentRepository.findByOrderId(order.getId()).orElseThrow();
	}

	private PayConfirmDTO confirmRequestOf(Orders order) {
		return new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());
	}

	private void stubConfirm(int status, String body) {
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBody(body)));
	}
}
