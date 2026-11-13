package com.commerce.auth;

import java.util.Optional;

/**
 * 로그인 성공 뒤 돌아갈 경로. React가 로그인을 시작할 때 returnTo 파라미터로 넘긴다.
 * 사이트 안 경로("/"로 시작)만 받는다. "//host"나 "/\host"는 브라우저가 다른 사이트로 해석하므로 버린다.
 */
public final class LoginReturnTo {

	public static final String PARAM = "returnTo";
	public static final String SESSION_ATTR = LoginReturnTo.class.getName();

	private static final int MAX_LENGTH = 2048;

	private LoginReturnTo() {
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
