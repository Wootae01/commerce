package com.commerce.auth;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 로그인 성공 뒤 돌아갈 경로. React가 로그인을 시작할 때 returnTo 파라미터로 넘긴다.
 * 사이트 안 경로("/"로 시작)만 받는다. "//host"나 "/\host"는 브라우저가 다른 사이트로 해석하므로 버린다.
 * 네이버 로그인은 콜백 요청에서 성공하므로, 시작할 때 받은 값을 세션에 두었다가 성공할 때 꺼낸다.
 */
public final class LoginReturnTo {

	public static final String PARAM = "returnTo";
	public static final String SESSION_ATTR = LoginReturnTo.class.getName();

	private static final int MAX_LENGTH = 2048;

	private LoginReturnTo() {
	}

	// 로그인을 시작하는 요청의 returnTo를 세션에 기억한다.
	// 사이트 밖 주소거나 없으면 전에 기억한 값을 지워, 지난 로그인의 경로로 가지 않게 한다
	public static void remember(HttpServletRequest request) {
		sanitize(request.getParameter(PARAM)).ifPresentOrElse(
			path -> request.getSession().setAttribute(SESSION_ATTR, path),
			() -> {
				HttpSession session = request.getSession(false);
				if (session != null) {
					session.removeAttribute(SESSION_ATTR);
				}
			});
	}

	// 로그인 성공 때 기억한 경로를 꺼낸다. 한 번 쓰면 지운다
	public static Optional<String> take(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(SESSION_ATTR) instanceof String path)) {
			return Optional.empty();
		}
		session.removeAttribute(SESSION_ATTR);
		return Optional.of(path);
	}

	public static Optional<String> sanitize(String value) {
		if (value == null || value.isEmpty() || value.length() > MAX_LENGTH) {
			return Optional.empty();
		}
		if (!value.startsWith("/") || value.startsWith("//") || value.indexOf('\\') >= 0) {
			return Optional.empty();
		}
		// 줄바꿈 등 제어 문자가 있으면 헤더를 깨뜨릴 수 있다
		if (value.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
			return Optional.empty();
		}
		return Optional.of(value);
	}
}
