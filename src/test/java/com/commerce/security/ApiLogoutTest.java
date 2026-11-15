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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.commerce.config.SecurityOnlyTest;

import jakarta.servlet.http.Cookie;

/**
 * React는 POST /api/logout으로 로그아웃한다. 화면 체인의 /logout은 세션의 CSRF 토큰을 요구해 React에서 쓸 수 없다.
 * /api 체인처럼 XSRF-TOKEN 쿠키의 토큰을 X-XSRF-TOKEN 헤더로 받고, 화면을 옮기지 않고 204로 응답한다.
 */
@SecurityOnlyTest
@ActiveProfiles("test")
class ApiLogoutTest {

	private static final String LOGOUT_URL = "/api/logout";
	private static final String API_CSRF_COOKIE = "XSRF-TOKEN";

	@Autowired WebApplicationContext context;
	MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	@DisplayName("CSRF 헤더를 담아 POST하면 세션을 끝내고 JSESSIONID를 지우며 204로 응답한다")
	void logout_withCsrfHeader_endsSession() throws Exception {
		Cookie token = issueApiCsrfCookie();
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(post(LOGOUT_URL)
				.with(user("user"))
				.session(session)
				.cookie(token)
				.header("X-XSRF-TOKEN", token.getValue()))
			.andExpect(status().isNoContent())
			.andExpect(header().doesNotExist("Location"))
			.andExpect(cookie().maxAge("JSESSIONID", 0));

		assertThat(session.isInvalid()).isTrue();
	}

	@Test
	@DisplayName("CSRF 헤더가 없으면 403이고 세션은 그대로다 (다른 사이트가 로그아웃시키지 못한다)")
	void logout_withoutCsrfHeader_forbidden() throws Exception {
		Cookie token = issueApiCsrfCookie();
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(post(LOGOUT_URL)
				.with(user("user"))
				.session(session)
				.cookie(token))
			.andExpect(status().isForbidden());

		assertThat(session.isInvalid()).isFalse();
	}

	@Test
	@DisplayName("세션이 이미 끝났어도 204로 응답한다 (React는 로그아웃 상태로 두면 된다)")
	void logout_withoutLogin_noContent() throws Exception {
		Cookie token = issueApiCsrfCookie();

		mockMvc.perform(post(LOGOUT_URL)
				.cookie(token)
				.header("X-XSRF-TOKEN", token.getValue()))
			.andExpect(status().isNoContent());
	}

	private Cookie issueApiCsrfCookie() throws Exception {
		Cookie token = mockMvc.perform(get("/api/users/me").with(user("user")))
			.andReturn().getResponse().getCookie(API_CSRF_COOKIE);
		assertThat(token).isNotNull();
		return token;
	}
}
