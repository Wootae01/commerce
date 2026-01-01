package com.commerce.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.stereotype.Component;

@Component
public class SocialClientRegistration {

	@Value("${naver.client-id}")
	private String NAVER_CLIENT_ID;

	@Value("${naver.client-secret}")
	private String NAVER_CLIENT_SECRET;

	public ClientRegistration naverClientRegistration() {
		return ClientRegistration.withRegistrationId("naver")
			.clientId(NAVER_CLIENT_ID)
			.clientSecret(NAVER_CLIENT_SECRET)
			.redirectUri("http://localhost:8080/login/oauth2/code/naver")
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.scope("name", "email", "phone")
			.authorizationUri("https://nid.naver.com/oauth2.0/authorize")
			.tokenUri("https://nid.naver.com/oauth2.0/token")
			.userInfoUri("https://openapi.naver.com/v1/nid/me")
			.userNameAttributeName("response")
			.build();
	}

}
