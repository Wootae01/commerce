package com.commerce.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.commerce.auth.LoginReturnTo;
import com.commerce.auth.LoginSuccessHandler;
import com.commerce.config.IntegrationTest;

/**
 * React에서 로그인을 시작할 때 returnTo로 넘긴 경로로, 로그인 성공 뒤 돌려보낸다.
 * 사이트 안 경로만 받아 로그인을 미끼로 다른 사이트로 보내는 것(오픈 리다이렉트)을 막는다.
 */
@IntegrationTest
class LoginReturnToTest {

	@Autowired WebApplicationContext context;
	MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@ParameterizedTest
	@ValueSource(strings = {"/", "/cart", "/products/3?option=1", "/orders/abc#detail"})
	@DisplayName("사이트 안 경로는 받는다")
	void sanitize_acceptsSitePath(String path) {
		assertThat(LoginReturnTo.sanitize(path)).contains(path);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "cart", "//evil.com", "/\\evil.com", "https://evil.com", "/cart\r\nSet-Cookie: x=1",
		"javascript:alert(1)"})
	@DisplayName("사이트 밖으로 나갈 수 있는 값은 버린다")
	void sanitize_rejectsOtherValues(String value) {
		assertThat(LoginReturnTo.sanitize(value)).isEmpty();
	}

	@Test
	@DisplayName("null은 버린다")
	void sanitize_rejectsNull() {
		assertThat(LoginReturnTo.sanitize(null)).isEmpty();
	}

	@Test
	@DisplayName("네이버 로그인을 returnTo와 시작하면 세션에 기억한다")
	void oauthStart_remembersReturnTo() throws Exception {
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(get("/oauth2/authorization/naver").param(LoginReturnTo.PARAM, "/cart").session(session))
			.andExpect(status().is3xxRedirection());

		assertThat(session.getAttribute(LoginReturnTo.SESSION_ATTR)).isEqualTo("/cart");
	}

	@Test
	@DisplayName("사이트 밖 주소로 시작하면 기억하지 않고, 전에 기억한 값도 지운다")
	void oauthStart_ignoresExternalReturnTo() throws Exception {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(LoginReturnTo.SESSION_ATTR, "/cart");

		mockMvc.perform(get("/oauth2/authorization/naver").param(LoginReturnTo.PARAM, "//evil.com").session(session))
			.andExpect(status().is3xxRedirection());

		assertThat(session.getAttribute(LoginReturnTo.SESSION_ATTR)).isNull();
	}

	@Test
	@DisplayName("로그인 성공: 기억한 경로로 보내고 세션에서 지운다")
	void success_redirectsToRememberedPath() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.getSession().setAttribute(LoginReturnTo.SESSION_ATTR, "/cart");
		MockHttpServletResponse response = new MockHttpServletResponse();

		LoginSuccessHandler.forOAuth().onAuthenticationSuccess(request, response, authentication());

		assertThat(response.getRedirectedUrl()).isEqualTo("/cart");
		assertThat(request.getSession().getAttribute(LoginReturnTo.SESSION_ATTR)).isNull();
	}

	@Test
	@DisplayName("로그인 성공: 기억한 경로가 없으면 지금처럼 홈으로 보낸다")
	void success_withoutReturnTo_redirectsHome() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		LoginSuccessHandler.forFormLogin().onAuthenticationSuccess(request, response, authentication());

		assertThat(response.getRedirectedUrl()).isEqualTo("/");
	}

	private static TestingAuthenticationToken authentication() {
		return new TestingAuthenticationToken("user", null, "ROLE_USER");
	}
}
