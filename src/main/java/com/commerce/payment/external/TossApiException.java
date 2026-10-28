package com.commerce.payment.external;

import org.springframework.http.HttpStatusCode;

import com.commerce.common.code.ApiResponseCode;
import com.commerce.common.exception.ApiException;

import lombok.Getter;

/**
 * 토스가 에러 응답을 준 경우.
 * 응답 코드는 그대로 두고, 결과를 분류할 수 있게 토스 응답의 HTTP 상태와 에러 코드를 함께 담는다.
 */
@Getter
public class TossApiException extends ApiException {

	private final HttpStatusCode tossStatus;
	private final String tossCode; // 응답 본문의 code. 본문이 없거나 JSON이 아니면 빈 문자열

	public TossApiException(ApiResponseCode responseCode, HttpStatusCode tossStatus, String tossCode) {
		super(responseCode);
		this.tossStatus = tossStatus;
		this.tossCode = tossCode;
	}

	public boolean isServerError() {
		return tossStatus.is5xxServerError();
	}

	// 승인 여부를 알 수 없는 응답. 401은 멱등키에 저장되지 않고(#10), 429·5xx는 토스 쪽 상황이라 결제 조회로 확인한다
	public boolean isResultUnknown() {
		int status = tossStatus.value();
		return status == 401 || status == 429 || tossStatus.is5xxServerError();
	}

	// 같은 멱등키의 이전 요청을 토스가 아직 처리 중
	public boolean isIdempotentRequestProcessing() {
		return tossStatus.value() == 409 && "IDEMPOTENT_REQUEST_PROCESSING".equals(tossCode);
	}
}
