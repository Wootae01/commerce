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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;

import com.commerce.auth.CustomClientRegistrationRepo;
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
	@Bean
	@Order(1)
	public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/api/**")
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(apiAuthenticationEntryPoint));

		return http.build();
	}

	// (3) 일반 사용자용 보안 설정
	@Bean
	@Profile({"prod", "dev", "test"})
	public SecurityFilterChain userSecurity(HttpSecurity http) throws Exception {
		http
			.httpBasic(basic -> basic.disable())

			.oauth2Login(oauth2 -> oauth2
				.loginPage("/login")
				.clientRegistrationRepository(clientRegistrationRepo.clientRegistrationRepository())
				.userInfoEndpoint(userInfo -> userInfo.userService(customOauth2UserService)))

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

		return http.build();
	}

	@Bean
	@Profile("local")
	public SecurityFilterChain userSecurityDev(HttpSecurity http, @Qualifier("devUserDetailService")
		UserDetailsService devUserDetailService) throws Exception {
		http
			.httpBasic(basic -> basic.disable())

			// local에서는 로컬 폼 로그인 추가
			.formLogin(form -> form
				.loginPage("/login")
				.loginProcessingUrl("/login")   // POST /login 처리
				.defaultSuccessUrl("/", true)
				.permitAll()
			)
			.userDetailsService(devUserDetailService)

			.oauth2Login(oauth2 -> oauth2
				.loginPage("/login")
				.clientRegistrationRepository(clientRegistrationRepo.clientRegistrationRepository())
				.userInfoEndpoint(userInfo -> userInfo.userService(customOauth2UserService))
			)

			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/", "/home", "/login/**", "/oauth2/**", "/uploads/**",
					"/products/*", "/error/**", "/actuator/health", "/actuator/prometheus", "/images/**",
						"/favicon.ico"
				).permitAll()
				.anyRequest().authenticated()
			)
			.logout(logout -> logout
				.logoutUrl("/logout")
				.logoutSuccessUrl("/")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID")
			);

		return http.build();
	}
}
