package com.commerce.common.util;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.commerce.auth.PrincipalDetails;
import com.commerce.admin.domain.Admin;
import com.commerce.user.domain.User;
import com.commerce.common.enums.RoleType;
import com.commerce.auth.dto.CustomOauth2User;
import com.commerce.admin.service.AdminService;
import com.commerce.user.service.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SecurityUtil {
	private final UserService userService;
	private final AdminService adminService;

	public User getCurrentUser() {
		Authentication auth = getAuthentication();
		String username = extractUsername(auth);
		return userService.findByUsername(username);
	}

	public Admin getCurrentAdmin() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (hasRole(auth, RoleType.ROLE_ADMIN)) {
			String username = extractUsername(auth);
			return adminService.findByUsername(username);
		}
		throw new AccessDeniedException("관리자 로그인 상태가 아닙니다.");
	}

	private String extractUsername(Authentication auth) {

		Object principal = auth.getPrincipal();

		if (principal instanceof CustomOauth2User customOauth2User) {
			return customOauth2User.getUsername();
		} else if (principal instanceof PrincipalDetails principalDetails) {
			return principalDetails.getUsername();
		} else if (principal instanceof UserDetails userDetails) {
			return userDetails.getUsername();
		}
		throw new IllegalStateException("지원하지 않는 principle 타입: " + principal.getClass());

	}

	private boolean hasRole(Authentication auth, RoleType role) {
		if (auth == null) return false;

		return auth.getAuthorities().stream()
			.anyMatch(a -> role.name().equals(a.getAuthority()));
	}

	private Authentication getAuthentication() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
			throw new AuthenticationCredentialsNotFoundException("로그인 상태가 아닙니다.");
		}
		return authentication;
	}
}
