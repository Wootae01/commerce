package com.commerce.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Profile({"local", "dev"})
@Configuration
public class DevAuthUsers {

	@Bean
	@Qualifier("devUserDetailService")
	public UserDetailsService devUserDetailService(PasswordEncoder passwordEncoder) {
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
