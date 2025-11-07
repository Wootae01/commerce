package com.commerce.dto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.commerce.domain.enums.RoleType;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CustomOauth2User implements OAuth2User {

	private final Oauth2Response oauth2Response;
	private final RoleType roleType;


	@Override
	public Map<String, Object> getAttributes() {
		return Map.of();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() { // role 값

		Collection<GrantedAuthority> collection = new ArrayList<>();
		collection.add(new GrantedAuthority() {
			@Override
			public String getAuthority() {
				return roleType.name();
			}
		});

		return collection;

	}

	@Override
	public String getName() {
		return oauth2Response.getName();
	}

	public String getUsername() {
		return oauth2Response.getProvider() + " " + oauth2Response.getProviderId();
	}

}
