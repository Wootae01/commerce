package com.commerce.common.config;

import java.util.function.Supplier;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * React(SPA)가 XSRF-TOKEN 쿠키 값을 그대로 X-XSRF-TOKEN 헤더에 담아 보내는 방식을 받는다.
 * Spring Security 문서의 SPA 설정을 따른다.
 *
 * - 헤더로 온 값은 쿠키에 들어 있던 원래 값이므로 그대로 비교한다.
 * - 그 밖(폼 파라미터 등)은 BREACH 대비로 섞은(XOR) 값으로 보고 풀어서 비교한다.
 * - 요청마다 토큰을 꺼내, 쿠키가 없으면 응답에 새로 내려가게 한다.
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

	private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
	private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
		this.xor.handle(request, response, csrfToken);
		// 지연 로딩되는 토큰을 꺼내야 저장소가 쿠키를 쓴다
		csrfToken.get();
	}

	@Override
	public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
		String headerValue = request.getHeader(csrfToken.getHeaderName());
		return (StringUtils.hasText(headerValue) ? this.plain : this.xor).resolveCsrfTokenValue(request, csrfToken);
	}
}
