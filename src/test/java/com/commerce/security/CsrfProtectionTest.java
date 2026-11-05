package com.commerce.security;

import static org.assertj.core.api.Assertions.*;
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

import com.commerce.config.IntegrationTest;

/**
 * 세션 쿠키로 로그인한 사용자·관리자의 상태 변경 요청은 CSRF 토큰이 없으면 거절된다.
 * 다른 사이트가 로그인한 브라우저를 이용해 몰래 보낸 요청에는 토큰이 없다.
 */
@IntegrationTest
class CsrfProtectionTest {

	// 요청이 보안 필터를 통과하는지만 보므로 실제 주문은 없어도 된다
	private static final String ORDER_NUMBER = "0".repeat(32);

	@Autowired WebApplicationContext context;
	MockMvc mockMvc;

	// @AutoConfigureMockMvc를 쓰면 컨텍스트(커넥션 풀)가 하나 더 생겨 테스트 MySQL의 max_connections를 넘는다.
	// @IntegrationTest만 붙은 다른 테스트와 컨텍스트를 같이 쓰도록 MockMvc를 직접 만든다
	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	@DisplayName("관리자: 토큰 없이 주문 상태 변경을 보내면 403")
	void admin_withoutToken_forbidden() throws Exception {
		mockMvc.perform(post("/admin/orders/status")
				.with(user("admin").roles("ADMIN")))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("관리자: 토큰을 붙이면 주문 상태 변경이 처리된다")
	void admin_withToken_passes() throws Exception {
		mockMvc.perform(post("/admin/orders/status")
				.with(user("admin").roles("ADMIN"))
				.with(csrf()))
			.andExpect(redirectedUrl("/admin/orders?page=0"));
	}

	@Test
	@DisplayName("사용자: 토큰 없이 주문 취소를 보내면 403")
	void user_withoutToken_forbidden() throws Exception {
		mockMvc.perform(post("/pay/{orderNumber}/cancel", ORDER_NUMBER)
				.with(user("user")))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("사용자: 토큰을 붙이면 주문 취소가 처리된다")
	void user_withToken_passes() throws Exception {
		mockMvc.perform(post("/pay/{orderNumber}/cancel", ORDER_NUMBER)
				.with(user("user"))
				.with(csrf()))
			.andExpect(status().isOk())
			.andExpect(view().name("order-cancel"));
	}

	@Test
	@DisplayName("API: 토큰 없이 주문 취소를 보내면 403")
	void api_withoutToken_forbidden() throws Exception {
		mockMvc.perform(post("/api/orders/{orderNumber}/cancel", ORDER_NUMBER)
				.with(user("user")))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("API: 토큰을 붙이면 주문 취소가 컨트롤러까지 간다")
	void api_withToken_passes() throws Exception {
		// 테스트 사용자가 DB에 없어서 컨트롤러가 USER_NOT_FOUND(404)로 응답한다. 보안 필터는 통과했다는 뜻이다
		mockMvc.perform(post("/api/orders/{orderNumber}/cancel", ORDER_NUMBER)
				.with(user("user"))
				.with(csrf()))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("화면: 관리자 상품 목록의 홈 노출 폼에 토큰이 들어 있다")
	void adminProducts_featureForm_hasToken() throws Exception {
		String html = mockMvc.perform(get("/admin/products")
				.with(user("admin").roles("ADMIN")))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		String featureForm = html.substring(html.indexOf("id=\"featureForm\""));
		featureForm = featureForm.substring(0, featureForm.indexOf("</form>"));
		assertThat(featureForm).contains("name=\"_csrf\"");
	}

	@Test
	@DisplayName("화면: 결제 대기 화면에 fetch용 토큰 meta가 있다")
	void payLoading_hasTokenMeta() throws Exception {
		String html = mockMvc.perform(get("/pay/loading")
				.with(user("user")))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(html)
			.containsPattern("<meta name=\"_csrf\" content=\"[^\"]+\"")
			.contains("<meta name=\"_csrf_header\" content=\"X-CSRF-TOKEN\"");
	}

	@Test
	@DisplayName("화면: 로그인 화면에 토큰 meta가 있다 (local 폼 로그인·k6가 쓴다)")
	void login_hasTokenMeta() throws Exception {
		String html = mockMvc.perform(get("/login"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		assertThat(html).containsPattern("<meta name=\"_csrf\" content=\"[^\"]+\"");
	}
}
