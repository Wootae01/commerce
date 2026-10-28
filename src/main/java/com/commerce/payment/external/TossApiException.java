package com.commerce.payment.external;

import org.springframework.http.HttpStatusCode;

import com.commerce.common.code.ApiResponseCode;
import com.commerce.common.exception.ApiException;

import lombok.Getter;

/**
 * 토스가 에러 응답을 준 경우.
 * 응답 코드는 그대로 두고, 재요청 여부를 정할 수 있게 토스 응답의 HTTP 상태를 함께 담는다.
 */
@Getter
public class TossApiException extends ApiException {

	private final HttpStatusCode tossStatus;

	public TossApiException(ApiResponseCode responseCode, HttpStatusCode tossStatus) {
		super(responseCode);
		this.tossStatus = tossStatus;
	}

	public boolean isServerError() {
		return tossStatus.is5xxServerError();
	}
}
