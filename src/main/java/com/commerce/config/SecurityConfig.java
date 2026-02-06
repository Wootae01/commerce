	package com.commerce.config;

	import org.springframework.beans.factory.annotation.Qualifier;
	import org.springframework.context.annotation.Bean;
	import org.springframework.context.annotation.Configuration;
	import org.springframework.context.annotation.Profile;
	import org.springframework.security.config.annotation.web.builders.HttpSecurity;
	import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
	import org.springframework.security.core.userdetails.UserDetailsService;
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

		// (1) 관리자용 보안 설정
		@Bean
		public SecurityFilterChain adminSecurity(HttpSecurity http) throws Exception {
			http
				.securityMatcher("/admin/**") // admin 경로만 적용
				.csrf(csrf -> csrf.disable())
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
					.accessDeniedHandler((req, res, ex) -> res.sendRedirect("/admin/login")));

			return http.build();
		}

		// (2) 일반 사용자용 보안 설정
		@Bean
		@Profile("prod")
		public SecurityFilterChain userSecurity(HttpSecurity http) throws Exception {
			http
				.csrf(csrf -> csrf.disable())
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
		@Profile({"local", "dev"})
		public SecurityFilterChain userSecurityDev(HttpSecurity http, @Qualifier("devUserDetailService")
			UserDetailsService devUserDetailService) throws Exception {
			http
				.csrf(csrf -> csrf.disable())
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
