package com.commerce.auth;

import java.io.IOException;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 로그인을 시작하는 요청(네이버 로그인 시작, local 폼 로그인)의 returnTo를 세션에 기억한다.
 */
public class LoginReturnToFilter extends OncePerRequestFilter {

	private static final RequestMatcher LOGIN_START = new OrRequestMatcher(
		PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/oauth2/authorization/**"),
		PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/login"));

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !LOGIN_START.matches(request);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {

		LoginReturnTo.remember(request);
		chain.doFilter(request, response);
	}
}
