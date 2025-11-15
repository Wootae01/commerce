package com.commerce.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.commerce.domain.User;
import com.commerce.dto.CustomOauth2User;

@Component
public class SecurityUtil {
	public String getCurrentUserName() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Object principal = authentication.getPrincipal();

		if (principal instanceof CustomOauth2User) {

			String username = ((CustomOauth2User)principal).getUsername();
			return username;
		} else {
			throw new RuntimeException("일반 사용자 로그인 상태가 아닙니다.");
		}
	}
}
