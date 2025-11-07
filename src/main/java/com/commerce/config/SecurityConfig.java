package com.commerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.commerce.oauth2.CustomClientRegistrationRepo;
import com.commerce.service.CustomOauth2UserService;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final CustomOauth2UserService customOauth2UserService;
	private final CustomClientRegistrationRepo clientRegistrationRepo;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception{

		http
			.csrf((csrf) -> csrf.disable());

		http
			.formLogin((login) -> login.disable());

		http
			.httpBasic((basic) -> basic.disable());

		http
			.oauth2Login((oauth2) -> oauth2
				.loginPage("/login")
				.clientRegistrationRepository(clientRegistrationRepo.clientRegistrationRepository())
				.userInfoEndpoint(userInfoEndpointConfig ->
					userInfoEndpointConfig.userService(customOauth2UserService)));

		http
			.authorizeHttpRequests((auth) -> auth
				.requestMatchers("/", "/oauth2/**", "/login/**", "/home")
				.permitAll()
				.anyRequest().authenticated());


		return http.build();
	}
}
