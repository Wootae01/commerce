package com.commerce.order.controller;

import com.commerce.common.code.ApiResponseCode;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.enums.OrderStatus;

// 주문 취소 결과를 응답 코드로 바꾼다(/api/orders/{orderNumber}/cancel)
// 토스가 거절하면 예외로 끝나 지금과 같은 오류 응답이 된다
final class CancelResponseCode {

	private CancelResponseCode() {
	}

	// CANCELED → 200, 결과를 모르면(CANCEL_REQUESTED) 202 취소 처리 중. 보정 스케줄러가 끝낸다
	static ApiResponseCode from(OrderStatus status) {
		return switch (status) {
			case CANCEL_REQUESTED -> GeneralResponseCode.ORDER_CANCEL_PROCESSING;
			default -> GeneralResponseCode.OK;
		};
	}
}
