package com.commerce.common.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;

import com.commerce.auth.CustomClientRegistrationRepo;
import com.commerce.auth.LoginReturnToFilter;
import com.commerce.auth.LoginSuccessHandler;
import com.commerce.auth.service.CustomOauth2UserService;
import com.commerce.auth.service.CustomUserDetailService;
import com.commerce.common.exception.ApiAuthenticationEntryPoint;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final CustomOauth2UserService customOauth2UserService;
	private final CustomClientRegistrationRepo clientRegistrationRepo;
	private final CustomUserDetailService customUserDetailService;
	private final ApiAuthenticationEntryPoint apiAuthenticationEntryPoint;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// (1) 관리자용 보안 설정
	@Bean
	public SecurityFilterChain adminSecurity(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/admin/**") // admin 경로만 적용
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form
				.loginPage("/admin/login")
				.loginProcessingUrl("/admin/login")
				.defaultSuccessUrl("/admin/dashboard", true)
				.permitAll())
			.userDetailsService(customUserDetailService)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/admin/login", "/admin/login/**").permitAll()
				.anyRequest().hasRole("ADMIN"))
			.exceptionHandling(exception -> exception
				.accessDeniedHandler(adminAccessDeniedHandler()));

		return http.build();
	}

	// 권한이 없으면 로그인 화면으로 보낸다.
	// CSRF 토큰이 틀린 요청은 로그인과 상관없으므로 403으로 거절한다.
	private AccessDeniedHandler adminAccessDeniedHandler() {
		AccessDeniedHandler forbidden = new AccessDeniedHandlerImpl();
		return (req, res, ex) -> {
			if (ex instanceof CsrfException) {
				forbidden.handle(req, res, ex);
				return;
			}
			res.sendRedirect("/admin/login");
		};
	}

	// (2) REST API용 보안 설정
	// 로그인은 기존 화면에서 하고, 세션으로 인증한다.
	// 사용자용 체인과 분리해서, 인증 실패 시 로그인 페이지 리다이렉트 대신 401 JSON으로 응답한다.
	// CSRF 토큰은 React가 읽을 수 있게 XSRF-TOKEN 쿠키로 내려주고 X-XSRF-TOKEN 헤더로 받는다.
	// 화면(Thymeleaf) 체인은 지금처럼 세션에 저장한다. 화면의 POST는 /api로 보내지 않는다
	@Bean
	@Order(1)
	public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/api/**")
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable())
			.csrf(csrf -> csrf
				.csrfTokenRepository(apiCsrfTokenRepository())
				.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(apiAuthenticationEntryPoint));

		return http.build();
	}

	// JS가 읽어야 해서 HttpOnly를 끈다. Domain은 넣지 않아 지금 호스트에서만 쓰인다
	private static CookieCsrfTokenRepository apiCsrfTokenRepository() {
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}

	// (3) 일반 사용자용 보안 설정
	@Bean
	@Profile({"prod", "dev", "test"})
	public SecurityFilterChain userSecurity(HttpSecurity http) throws Exception {
		configureUserScreen(http);
		return http.build();
	}

	// local에서는 네이버 로그인에 더해 로컬 폼 로그인을 쓴다
	@Bean
	@Profile("local")
	public SecurityFilterChain userSecurityDev(HttpSecurity http, @Qualifier("devUserDetailService")
		UserDetailsService devUserDetailService) throws Exception {
		configureUserScreen(http);
		http
			.formLogin(form -> form
				.loginPage("/login")
				.loginProcessingUrl("/login")   // POST /login 처리
				.successHandler(LoginSuccessHandler.forFormLogin())
				.permitAll())
			.userDetailsService(devUserDetailService);

		return http.build();
	}

	// 일반 사용자 체인 공통: 네이버 로그인, 로그인 뒤 돌아갈 경로, 허용 경로, 로그아웃
	private void configureUserScreen(HttpSecurity http) throws Exception {
		http
			.httpBasic(basic -> basic.disable())

			.oauth2Login(oauth2 -> oauth2
				.loginPage("/login")
				.clientRegistrationRepository(clientRegistrationRepo.clientRegistrationRepository())
				.userInfoEndpoint(userInfo -> userInfo.userService(customOauth2UserService))
				.successHandler(LoginSuccessHandler.forOAuth()))
			// 로그인을 시작할 때 받은 returnTo를 기억해, 성공하면 그 경로로 보낸다
			.addFilterBefore(new LoginReturnToFilter(), OAuth2AuthorizationRequestRedirectFilter.class)

			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/", "/home", "/login/**", "/oauth2/**", "/uploads/**",
					"/products/*", "/error/**", "/actuator/health", "/actuator/prometheus", "/images/**",
						"/favicon.ico"
				).permitAll()
				.anyRequest().authenticated())
			.logout(logout -> logout
				.logoutUrl("/logout")
				.logoutSuccessUrl("/")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID"));
	}
}
