package com.commerce.payment.controller;

import com.commerce.common.code.ApiResponseCode;
import com.commerce.common.code.ExternalResponseCode;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.enums.OrderStatus;

// 결제 확정 결과를 응답 코드로 바꾼다. /pay/confirm, /api/payments/confirm이 함께 쓴다
final class PayConfirmResponseCode {

	private PayConfirmResponseCode() {
	}

	// PAID → 200, 결과를 모르면(PAYMENT_PENDING) 202 확인 중, CANCELED → 실패
	static ApiResponseCode from(OrderStatus status) {
		return switch (status) {
			case PAID -> GeneralResponseCode.OK;
			case PAYMENT_PENDING -> GeneralResponseCode.PAYMENT_CONFIRMING;
			default -> ExternalResponseCode.PG_APPROVAL_ERROR;
		};
	}
}
