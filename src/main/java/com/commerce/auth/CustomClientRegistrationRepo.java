package com.commerce.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration

public class CustomClientRegistrationRepo {

	private final SocialClientRegistration socialClientRegistration;

	public CustomClientRegistrationRepo(SocialClientRegistration socialClientRegistration) {
		this.socialClientRegistration = socialClientRegistration;
	}

	public InMemoryClientRegistrationRepository clientRegistrationRepository() {
		return new InMemoryClientRegistrationRepository(socialClientRegistration.naverClientRegistration());
	}
}

