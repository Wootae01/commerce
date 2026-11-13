package com.commerce.auth;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 로그인 성공 뒤 이동. 세션에 기억한 returnTo가 있으면 그 경로로, 없으면 원래 동작대로 보낸다.
 * returnTo는 LoginReturnToFilter가 검증해 넣은 값이고, 한 번 쓰면 지운다.
 */
public class LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

	private LoginSuccessHandler(boolean alwaysUseDefaultTargetUrl) {
		setDefaultTargetUrl("/");
		setAlwaysUseDefaultTargetUrl(alwaysUseDefaultTargetUrl);
	}

	// 네이버 로그인: 로그인 전에 막힌 화면(저장된 요청)이 있으면 그리로, 없으면 홈
	public static LoginSuccessHandler forOAuth() {
		return new LoginSuccessHandler(false);
	}

	// local 폼 로그인: 항상 홈
	public static LoginSuccessHandler forFormLogin() {
		return new LoginSuccessHandler(true);
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
		Authentication authentication) throws ServletException, IOException {

		HttpSession session = request.getSession(false);
		Object returnTo = session == null ? null : session.getAttribute(LoginReturnTo.SESSION_ATTR);
		if (returnTo instanceof String path) {
			session.removeAttribute(LoginReturnTo.SESSION_ATTR);
			clearAuthenticationAttributes(request);
			getRedirectStrategy().sendRedirect(request, response, path);
			return;
		}

		super.onAuthenticationSuccess(request, response, authentication);
	}
}
