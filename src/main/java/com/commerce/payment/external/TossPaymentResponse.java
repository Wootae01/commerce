package com.commerce.payment.external;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스 Payment 객체(승인·조회·취소 응답) 중 우리가 읽는 필드.
 * 값의 형식이 예상과 달라도 응답 해석이 실패하지 않게 문자열로 받고, 변환은 쓰는 쪽에서 한다.
 * 해석이 실패하면 승인됐는데 결과를 모르는 상태가 되고, 같은 응답을 주는 조회도 실패해 PENDING에 머문다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
	String status,
	String method,
	String approvedAt,
	Failure failure,
	List<Cancel> cancels) {

	// 없는 값은 비워 둔다. status가 null이면 상태별 분기(switch)에서 예외가 난다
	public TossPaymentResponse {
		status = status == null ? "" : status;
		failure = failure == null ? new Failure(null, null) : failure;
		cancels = cancels == null ? List.of() : cancels;
	}

	// 이번 취소의 금액. 전체 취소만 하므로 취소 내역은 1건이다
	public int firstCancelAmount() {
		return cancels.isEmpty() ? 0 : cancels.get(0).cancelAmount();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Failure(String code, String message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Cancel(int cancelAmount) {
	}
}
