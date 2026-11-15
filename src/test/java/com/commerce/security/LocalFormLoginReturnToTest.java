package com.commerce.security;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.commerce.auth.LoginReturnTo;
import com.commerce.config.SecurityOnlyTest;

/**
 * local 폼 로그인(POST /login)도 returnTo로 넘긴 경로로 돌려보낸다.
 * 폼 로그인은 local 체인에만 있어서 test 프로필의 @IntegrationTest로는 확인할 수 없다.
 */
@SecurityOnlyTest
@ActiveProfiles("local")
class LocalFormLoginReturnToTest {

	@Autowired WebApplicationContext context;
	MockMvc mockMvc;

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	@DisplayName("returnTo와 로그인하면 그 경로로 보낸다")
	void formLogin_redirectsToReturnTo() throws Exception {
		mockMvc.perform(login().param(LoginReturnTo.PARAM, "/cart"))
			.andExpect(redirectedUrl("/cart"));
	}

	@Test
	@DisplayName("returnTo가 사이트 밖 주소면 홈으로 보낸다")
	void formLogin_externalReturnTo_redirectsHome() throws Exception {
		mockMvc.perform(login().param(LoginReturnTo.PARAM, "//evil.com"))
			.andExpect(redirectedUrl("/"));
	}

	@Test
	@DisplayName("returnTo가 없으면 지금처럼 홈으로 보낸다")
	void formLogin_withoutReturnTo_redirectsHome() throws Exception {
		mockMvc.perform(login())
			.andExpect(redirectedUrl("/"));
	}

	private static MockHttpServletRequestBuilder login() {
		return post("/login").with(csrf()).param("username", "user1").param("password", "password!");
	}
}
