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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.commerce.config.SecurityOnlyTest;

import jakarta.servlet.http.Cookie;

/**
 * /api/** 체인은 CSRF 토큰을 React가 읽을 수 있는 XSRF-TOKEN 쿠키로 내려주고 X-XSRF-TOKEN 헤더로 받는다.
 * 화면(Thymeleaf) 체인은 지금처럼 세션에 저장하므로 이 쿠키를 내려주지 않는다.
 * 저장소를 테스트에서 넣지 않고 SecurityConfig 설정 그대로 확인한다. 컨트롤러가 없어 필터를 통과하면 404다.
 */
@SecurityOnlyTest
@ActiveProfiles("test")
class ApiCsrfCookieTest {

	private static final String API_CSRF_COOKIE = "XSRF-TOKEN";
	private static final String CANCEL_URL = "/api/orders/{orderNumber}/cancel";
	private static final String ORDER_NUMBER = "0".repeat(32);

	@Autowired WebApplicationContext context;
	MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	@DisplayName("API: 요청하면 React가 읽을 수 있는 XSRF-TOKEN 쿠키를 내려준다")
	void api_issuesReadableCsrfCookie() throws Exception {
		mockMvc.perform(get("/api/users/me").with(user("user")))
			.andExpect(cookie().exists(API_CSRF_COOKIE))
			.andExpect(cookie().httpOnly(API_CSRF_COOKIE, false));
	}

	@Test
	@DisplayName("API: 쿠키의 토큰을 X-XSRF-TOKEN 헤더에 담으면 보안 필터를 통과한다")
	void api_cookieTokenInHeader_passes() throws Exception {
		Cookie token = issueApiCsrfCookie();

		mockMvc.perform(post(CANCEL_URL, ORDER_NUMBER)
				.with(user("user"))
				.cookie(token)
				.header("X-XSRF-TOKEN", token.getValue()))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("API: 쿠키만 있고 헤더가 없으면 403 (다른 사이트의 요청에도 쿠키는 붙는다)")
	void api_cookieWithoutHeader_forbidden() throws Exception {
		Cookie token = issueApiCsrfCookie();

		mockMvc.perform(post(CANCEL_URL, ORDER_NUMBER)
				.with(user("user"))
				.cookie(token))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("API: 헤더 값이 쿠키와 다르면 403")
	void api_headerNotMatchingCookie_forbidden() throws Exception {
		Cookie token = issueApiCsrfCookie();

		mockMvc.perform(post(CANCEL_URL, ORDER_NUMBER)
				.with(user("user"))
				.cookie(token)
				.header("X-XSRF-TOKEN", "not-" + token.getValue()))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("화면: Thymeleaf 체인은 세션에 저장하므로 XSRF-TOKEN 쿠키를 내려주지 않는다")
	void screen_doesNotIssueCsrfCookie() throws Exception {
		mockMvc.perform(get("/pay/loading").with(user("user")))
			.andExpect(cookie().doesNotExist(API_CSRF_COOKIE));
	}

	private Cookie issueApiCsrfCookie() throws Exception {
		Cookie token = mockMvc.perform(get("/api/users/me").with(user("user")))
			.andReturn().getResponse().getCookie(API_CSRF_COOKIE);
		assertThat(token).isNotNull();
		return token;
	}
}
