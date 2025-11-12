package com.commerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.commerce.auth.CustomClientRegistrationRepo;
import com.commerce.service.CustomOauth2UserService;
import com.commerce.service.CustomUserDetailService;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final CustomOauth2UserService customOauth2UserService;
	private final CustomClientRegistrationRepo clientRegistrationRepo;
	private final CustomUserDetailService customUserDetailService;
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception{

		http
			.csrf((csrf) -> csrf.disable());

		http
			.httpBasic((basic) -> basic.disable());

		// 관리자 로그인
		http
			.formLogin(form -> form
				.loginPage("/admin/login")
				.loginProcessingUrl("/admin/login")
				.failureUrl("/admin/login?error")
				.defaultSuccessUrl("/", true)
				.permitAll())
			.userDetailsService(customUserDetailService);

		// sns 로그인
		http
			.oauth2Login((oauth2) -> oauth2
				.loginPage("/login")
				.clientRegistrationRepository(clientRegistrationRepo.clientRegistrationRepository())
				.userInfoEndpoint(userInfoEndpointConfig ->
					userInfoEndpointConfig.userService(customOauth2UserService)));

		// 접근 설정
		http
			.authorizeHttpRequests((auth) -> auth
				.requestMatchers("/admin/**").hasRole("ADMIN")
				.requestMatchers("/", "/oauth2/**", "/login/**", "/home", "/uploads/**")
				.permitAll()
				.anyRequest().authenticated());

		// 로그 아웃
		http
			.logout(logout -> logout
				.logoutUrl("/logout")
				.logoutSuccessUrl("/")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID"));


		return http.build();
	}
}
