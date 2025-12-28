package com.commerce.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Profile("dev")
@Configuration
public class DevAuthUsers {

	@Bean
	public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
		InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
		int n = 1000;
		String rawPw = "password!";
		for (int i = 0; i < n; i++) {
			manager.createUser(
				User.withUsername("user" + i)
					.password(passwordEncoder.encode(rawPw))
					.roles("USER")
					.build()
			);

		}
		return manager;
	}

}
