package com.commerce.common.util;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

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

	// DB 조회 없이 principal에서 username만 꺼낸다. 감사 필드(createdBy 등)와 soft delete의 deletedBy에 쓴다.
	public static Optional<String> findCurrentUsername() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
			return Optional.empty();
		}
		return Optional.of(extractUsername(auth));
	}

	public static String getCurrentUsername() {
		return extractUsername(getAuthentication());
	}

	private static String extractUsername(Authentication auth) {

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

	private static boolean hasRole(Authentication auth, RoleType role) {
		if (auth == null) return false;

		return auth.getAuthorities().stream()
			.anyMatch(a -> role.name().equals(a.getAuthority()));
	}

	private static Authentication getAuthentication() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
			throw new AuthenticationCredentialsNotFoundException("로그인 상태가 아닙니다.");
		}
		return authentication;
	}
}
