package com.commerce.common.dto;

import java.util.Map;

import org.springframework.http.ResponseEntity;

import com.commerce.common.code.ApiResponseCode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ErrorResponse {
	private Integer status;
	private String message;
	private Map<String, String> errors;

	public static ResponseEntity<ErrorResponse> toResponseEntity(ApiResponseCode responseCode, Map<String, String> errors) {
		return toResponseEntity(responseCode, responseCode.getMessage(), errors);
	}

	// ApiException에 직접 지정한 메시지가 있으면 코드의 기본 메시지 대신 사용한다.
	public static ResponseEntity<ErrorResponse> toResponseEntity(ApiResponseCode responseCode, String message,
		Map<String, String> errors) {
		return ResponseEntity.status(responseCode.getStatus())
			.body(ErrorResponse.builder()
				.status(responseCode.getStatus().value())
				.message(message)
				.errors(errors)
				.build());
	}
}
