package com.commerce.payment.service;

import com.commerce.common.code.GeneralResponseCode;

/**
 * 결제 실패 이유. 결제 실패로 주문이 취소될 때 Payment에 남긴다.
 * @param code 토스 에러 코드 또는 결제 조회 상태(ABORTED 등). 모르면 null. 사용자에게는 보여주지 않는다
 * @param message 사용자에게 보여줄 문장
 */
public record FailureReason(String code, String message) {

	// 토스 거절 응답의 code·message. 본문을 해석하지 못해 비어 있으면 code는 null, message는 기본 문장
	public static FailureReason ofTossError(String code, String message) {
		return new FailureReason(
			isBlank(code) ? null : code,
			isBlank(message) ? GeneralResponseCode.PAYMENT_REJECTED.getMessage() : message);
	}

	// 결제 조회로 실패를 확정한 경우(ABORTED·EXPIRED·CANCELED). 결제 객체의 failure가 있으면 그 code·message,
	// 없으면 조회 상태를 코드로, 우리 문장을 메시지로 남긴다
	public static FailureReason ofQueriedPayment(String status, String failureCode, String failureMessage) {
		if (!isBlank(failureMessage)) {
			return new FailureReason(isBlank(failureCode) ? null : failureCode, failureMessage);
		}
		return new FailureReason(status, messageOfStatus(status));
	}

	private static String messageOfStatus(String status) {
		return switch (status) {
			case "EXPIRED" -> "결제 시간이 만료되었습니다.";
			case "CANCELED" -> "결제가 취소되었습니다.";
			default -> "결제가 승인되지 않았습니다.";
		};
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
