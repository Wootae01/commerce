package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.commerce.order.domain.Orders;
import com.github.tomakehurst.wiremock.http.Fault;

/**
 * 주문 취소 결과 화면(/pay/{orderNumber}/cancel)과 API(/api/orders/{orderNumber}/cancel)의 응답.
 * 결과를 모르면(취소 처리 중) 실패가 아니라 처리 중으로 알린다.
 */
class CancelResponseTest extends PaymentIntegrationTestSupport {

	@Autowired WebApplicationContext context;
	MockMvc mockMvc;

	// @AutoConfigureMockMvc를 쓰면 컨텍스트가 하나 더 생겨 테스트 MySQL의 max_connections를 넘는다
	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	@DisplayName("화면: 취소 결과를 모르면 실패가 아니라 취소 요청을 접수했다고 보여준다")
	void page_unknown_processing() throws Exception {
		// given
		Orders order = paidOrder();
		stubConnectionLost(order);

		// when then
		mockMvc.perform(post("/pay/{orderNumber}/cancel", order.getOrderNumber())
				.with(user("username"))
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(view().name("order-cancel"))
			.andExpect(content().string(containsString("취소 요청을 접수했어요")))
			.andExpect(content().string(not(containsString("주문 취소에 실패했습니다"))));
	}

	@Test
	@DisplayName("화면: 토스가 취소를 거절하면 실패 화면에 토스가 준 문장을 보여준다")
	void page_rejected_tossMessage() throws Exception {
		// given
		Orders order = paidOrder();
		stubCancel(order, 403, "{ \"code\": \"NOT_CANCELABLE_PAYMENT\", \"message\": \"취소 할 수 없는 결제 입니다.\" }");

		// when then
		mockMvc.perform(post("/pay/{orderNumber}/cancel", order.getOrderNumber())
				.with(user("username"))
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString("주문 취소에 실패했습니다")))
			.andExpect(content().string(containsString("취소 할 수 없는 결제 입니다.")));
	}

	@Test
	@DisplayName("API: 취소 결과를 모르면 202와 취소 처리 중 상태를 돌려준다")
	void api_unknown_accepted() throws Exception {
		// given
		Orders order = paidOrder();
		stubConnectionLost(order);

		// when then
		mockMvc.perform(post("/api/orders/{orderNumber}/cancel", order.getOrderNumber())
				.with(user("username"))
				.with(csrf()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value(202))
			.andExpect(jsonPath("$.data.orderStatus").value("CANCEL_REQUESTED"));
	}

	@Test
	@DisplayName("API: 취소가 끝나면 200과 취소 완료 상태·환불 금액을 돌려준다")
	void api_canceled_ok() throws Exception {
		// given
		Orders order = paidOrder();
		stubCancel(order, 200, "{ \"status\": \"CANCELED\", \"method\": \"카드\" }");

		// when then
		mockMvc.perform(post("/api/orders/{orderNumber}/cancel", order.getOrderNumber())
				.with(user("username"))
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.orderStatus").value("CANCELED"))
			.andExpect(jsonPath("$.data.cancelAmount").value(order.getFinalPrice()));
	}

	private void stubConnectionLost(Orders order) {
		stubFor(post(urlEqualTo(cancelUrlOf(order)))
			.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
	}
}
