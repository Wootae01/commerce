package com.commerce.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.commerce.auth.CustomClientRegistrationRepo;
import com.commerce.auth.DevAuthUsers;
import com.commerce.auth.LoginReturnTo;
import com.commerce.auth.SocialClientRegistration;
import com.commerce.auth.service.CustomOauth2UserService;
import com.commerce.auth.service.CustomUserDetailService;
import com.commerce.common.config.SecurityConfig;
import com.commerce.common.exception.ApiAuthenticationEntryPoint;

/**
 * local 폼 로그인(POST /login)도 returnTo로 넘긴 경로로 돌려보낸다.
 * 폼 로그인은 local 체인에만 있어서 test 프로필의 @IntegrationTest로는 확인할 수 없다.
 * DB 없이 보안 설정만 올린다(공용 MySQL 커넥션을 쓰지 않는다).
 */
@SpringJUnitWebConfig(classes = {LocalFormLoginReturnToTest.MvcConfig.class, SecurityConfig.class, DevAuthUsers.class,
	CustomClientRegistrationRepo.class, SocialClientRegistration.class})
@ActiveProfiles("local")
@TestPropertySource(properties = {"naver.client-id=test-client", "naver.client-secret=test-secret"})
class LocalFormLoginReturnToTest {

	// 보안 설정의 경로 매칭(requestMatchers)이 Spring MVC를 쓴다
	@Configuration
	@EnableWebMvc
	static class MvcConfig {
	}

	@MockitoBean CustomOauth2UserService customOauth2UserService;
	@MockitoBean CustomUserDetailService customUserDetailService;
	@MockitoBean ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

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

	private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login() {
		return post("/login").with(csrf()).param("username", "user1").param("password", "password!");
	}
}
