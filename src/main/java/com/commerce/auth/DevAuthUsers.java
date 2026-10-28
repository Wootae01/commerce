package com.commerce.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Profile("local")
@Configuration
public class DevAuthUsers {

	@Bean
	@Qualifier("devUserDetailService")
	public UserDetailsService devUserDetailService(PasswordEncoder passwordEncoder) {
		InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
		int n = 1000;
		// 모두 같은 비밀번호라 BCrypt 해시는 한 번만 만든다 (1000번 만들면 컨텍스트마다 약 1분)
		String encodedPw = passwordEncoder.encode("password!");
		for (int i = 0; i < n; i++) {
			manager.createUser(
				User.withUsername("user" + i)
					.password(encodedPw)
					.roles("USER")
					.build()
			);

		}
		return manager;
	}

}
