package com.commerce.common.exception;

import org.springframework.http.HttpStatus;

import com.commerce.common.code.ApiResponseCode;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
	private final ApiResponseCode responseCode;

	public ApiException(ApiResponseCode responseCode) {
		this(responseCode, responseCode.getMessage());
	}

	public ApiException(ApiResponseCode responseCode, String message) {
		super(message);
		this.responseCode = responseCode;
	}

	public HttpStatus getStatus() {
		return responseCode.getStatus();
	}
}
