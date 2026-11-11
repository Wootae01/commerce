package com.commerce.payment.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스 Payment 객체(승인·조회·취소 응답) 중 우리가 읽는 필드.
 * 값의 형식이 예상과 달라도 응답 해석이 실패하지 않게 문자열로 받고, 변환은 쓰는 쪽에서 한다.
 * 해석이 실패하면 승인됐는데 결과를 모르는 상태가 되고, 같은 응답을 주는 조회도 실패해 PENDING에 머문다.
 * 취소 응답의 금액·수단은 읽지 않는다. 환불 금액·수단은 승인 때 저장한 결제 정보로 보여준다(전액 취소)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
	String status,
	String method,
	String approvedAt,
	Failure failure) {

	// 없는 값은 비워 둔다. status가 null이면 상태별 분기(switch)에서 예외가 난다
	public TossPaymentResponse {
		status = status == null ? "" : status;
		failure = failure == null ? new Failure(null, null) : failure;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Failure(String code, String message) {
	}
}
