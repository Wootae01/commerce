package com.commerce.service;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.commerce.domain.User;
import com.commerce.domain.enums.RoleType;
import com.commerce.dto.CustomOauth2User;
import com.commerce.dto.NaverResponse;
import com.commerce.dto.Oauth2Response;
import com.commerce.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomOauth2UserService extends DefaultOAuth2UserService {

	private final UserRepository userRepository;

	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException { // 사용자 정보 데이터를 인자로 받아옴

		OAuth2User oAuth2User = super.loadUser(userRequest); // user 정보 가져옴
		log.info("{}", oAuth2User.getAttributes());

		String registrationId = userRequest.getClientRegistration().getRegistrationId();

		Oauth2Response oauth2Response = null;
		if (registrationId.equals("naver")) {
			oauth2Response = new NaverResponse(oAuth2User.getAttributes());
		} else {
			return null;
		}

		RoleType role = null;
		String username = oauth2Response.getProvider() + " " + oauth2Response.getProviderId();

		User existUser = userRepository.findByUsername(username);

		if (existUser == null) {
			User user = new User();
			user.setUsername(username);
			user.setRole(RoleType.ROLE_USER);
			user.setName(oauth2Response.getName());
			user.setPhone(oauth2Response.getPhone());
			user.setEmail(oauth2Response.getEmail());
			userRepository.save(user);
		} else {
			existUser.setUsername(username);
			existUser.setPhone(oauth2Response.getPhone());
			existUser.setEmail(oauth2Response.getEmail());
			role = existUser.getRole();
			userRepository.save(existUser);
		}


		return new CustomOauth2User(oauth2Response, role);
	}
}
