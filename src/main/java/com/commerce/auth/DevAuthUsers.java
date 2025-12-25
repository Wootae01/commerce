package com.commerce.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Profile("dev")
@Configuration
public class DevAuthUsers {

	@Bean
	public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
		return new InMemoryUserDetailsManager(
			User.withUsername("user")
				.password(passwordEncoder.encode("user"))
				.roles("USER")
				.build()
		);
	}

}
