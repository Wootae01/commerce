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

	// PAID → 200, 결과를 모르면(PAYMENT_PENDING) 202 확인 중, PAYMENT_FAILED → 400 결제 실패
	// 결제 실패는 토스 장애가 아니라 정상적인 결과(카드 거절 등)라 502가 아니라 400이다
	// default를 두지 않는다. OrderStatus에 값이 생기면 여기서 컴파일 오류가 나 분기를 정하게 한다
	static ApiResponseCode from(OrderStatus status) {
		return switch (status) {
			// 결제는 승인됨. 확정 요청이 끝나기 전에 보정 스케줄러가 승인하고 그 뒤 취소·배송으로 바뀌었어도 확정 결과는 성공이다
			case PAID, PREPARING, SHIPPING, DELIVERED, CANCEL_REQUESTED, CANCELED -> GeneralResponseCode.OK;
			case PAYMENT_PENDING -> GeneralResponseCode.PAYMENT_CONFIRMING;
			case PAYMENT_FAILED -> GeneralResponseCode.PAYMENT_FAILED;
			// 토스를 부르기 전에 PAYMENT_PENDING으로 바뀌므로 확정 뒤에는 READY일 수 없다
			case READY -> throw new IllegalStateException("결제 확정 뒤 READY일 수 없습니다");
		};
	}

	// 결제 실패 응답 본문 { status, message }. message는 저장된 실패 이유, 없으면 응답 코드의 기본 문장
	static ResponseEntity<ErrorResponse> failed(ApiResponseCode code, PaySuccessDTO result) {
		String message = result.getFailureMessage() != null ? result.getFailureMessage() : code.getMessage();
		return ErrorResponse.toResponseEntity(code, message, null);
	}
}
