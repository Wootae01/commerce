package com.commerce.common.exception;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.commerce.common.code.AuthResponseCode;
import com.commerce.common.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * /api/** 요청이 인증되지 않았을 때 로그인 페이지로 리다이렉트하는 대신 401 JSON으로 응답한다.
 * 필터에서 막히는 요청이라 ApiExceptionHandler까지 오지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
		AuthenticationException authException) throws IOException {

		AuthResponseCode code = AuthResponseCode.UNAUTHORIZED;
		response.setStatus(code.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(),
			ErrorResponse.builder().status(code.getStatus().value()).message(code.getMessage()).build());
	}
}
