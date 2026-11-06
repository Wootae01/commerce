package com.commerce.payment.controller;

import org.springframework.http.ResponseEntity;

import com.commerce.common.code.ApiResponseCode;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.dto.ErrorResponse;
import com.commerce.common.enums.OrderStatus;
import com.commerce.payment.dto.PaySuccessDTO;

// 결제 확정 결과를 응답 코드로 바꾼다. /pay/confirm, /api/payments/confirm이 함께 쓴다
final class PayConfirmResponseCode {

	private PayConfirmResponseCode() {
	}

	// PAID → 200, 결과를 모르면(PAYMENT_PENDING) 202 확인 중, CANCELED → 400 결제 실패
	// 결제 실패는 토스 장애가 아니라 정상적인 결과(카드 거절 등)라 502가 아니라 400이다
	static ApiResponseCode from(OrderStatus status) {
		return switch (status) {
			case PAID -> GeneralResponseCode.OK;
			case PAYMENT_PENDING -> GeneralResponseCode.PAYMENT_CONFIRMING;
			default -> GeneralResponseCode.PAYMENT_REJECTED;
		};
	}

	// 결제 실패 응답 본문 { status, message }. message는 저장된 실패 이유, 없으면 응답 코드의 기본 문장
	static ResponseEntity<ErrorResponse> rejected(ApiResponseCode code, PaySuccessDTO result) {
		String message = result.getFailureMessage() != null ? result.getFailureMessage() : code.getMessage();
		return ErrorResponse.toResponseEntity(code, message, null);
	}
}
