package com.commerce.common.code;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ExternalResponseCode implements ApiResponseCode {
	PG_APPROVAL_ERROR(HttpStatus.BAD_GATEWAY, "결제 승인에 실패했습니다."),
	PG_CANCEL_ERROR(HttpStatus.BAD_GATEWAY, "결제 취소에 실패했습니다."),
	PG_QUERY_ERROR(HttpStatus.BAD_GATEWAY, "결제 조회에 실패했습니다.");

	private final HttpStatus status;
	private final String message;
}
