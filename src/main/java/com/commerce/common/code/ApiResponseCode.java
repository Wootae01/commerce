package com.commerce.common.code;

import org.springframework.http.HttpStatus;

public interface ApiResponseCode {
	HttpStatus getStatus();

	String getMessage();
}
