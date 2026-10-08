package com.commerce.common.exception;

import java.util.HashMap;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.commerce.common.code.AuthResponseCode;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.dto.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * @RestController 전용 예외 처리 (JSON 응답).
 * Thymeleaf 화면(@Controller)은 GlobalExceptionHandler가 처리하며,
 * 두 핸들러가 모두 적용되는 @RestController에서는 이 핸들러가 먼저 선택되도록 우선순위를 높인다.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApiException(ApiException e, HttpServletRequest request) {
		if (e.getStatus().is5xxServerError()) {
			log.error("ApiException: code={}, uri={}, message={}", e.getResponseCode(), request.getRequestURI(),
				e.getMessage(), e);
		} else {
			log.warn("ApiException: code={}, uri={}, message={}", e.getResponseCode(), request.getRequestURI(),
				e.getMessage());
		}
		return ErrorResponse.toResponseEntity(e.getResponseCode(), e.getMessage(), null);
	}

	// @RequestBody 검증 실패(MethodArgumentNotValidException)와 @ModelAttribute 검증 실패(BindException) 모두 처리
	@ExceptionHandler(BindException.class)
	public ResponseEntity<ErrorResponse> handleBindException(BindException e, HttpServletRequest request) {
		log.warn("BindException: uri={}, errorCount={}", request.getRequestURI(), e.getErrorCount());

		Map<String, String> errors = new HashMap<>();
		for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
			errors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return ErrorResponse.toResponseEntity(GeneralResponseCode.INVALID_REQUEST, errors);
	}

	// 파라미터 누락, 타입 불일치, 본문 파싱 실패, 메서드 파라미터 검증(@Min 등) 실패
	@ExceptionHandler({
		MissingServletRequestParameterException.class,
		MethodArgumentTypeMismatchException.class,
		HttpMessageNotReadableException.class,
		HandlerMethodValidationException.class,
		IllegalArgumentException.class
	})
	public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest request) {
		log.warn("Bad request: uri={}, exception={}, message={}", request.getRequestURI(),
			e.getClass().getSimpleName(), e.getMessage());
		return ErrorResponse.toResponseEntity(GeneralResponseCode.INVALID_REQUEST, null);
	}

	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException e,
		HttpServletRequest request) {
		log.warn("AuthenticationException: uri={}, message={}", request.getRequestURI(), e.getMessage());
		return ErrorResponse.toResponseEntity(AuthResponseCode.UNAUTHORIZED, null);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e,
		HttpServletRequest request) {
		log.warn("AccessDeniedException: uri={}, message={}", request.getRequestURI(), e.getMessage());
		return ErrorResponse.toResponseEntity(AuthResponseCode.FORBIDDEN, null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
		log.error("Unhandled exception: uri={}", request.getRequestURI(), e);
		return ErrorResponse.toResponseEntity(GeneralResponseCode.INTERNAL_SERVER_ERROR, null);
	}
}
