package com.commerce.domain.enums;

public enum OrderStatus {
	WAITING_FOR_PAYMENT("결제 대기"),
	WAITING_FOR_DEPOSIT("무통장 입금 대기"),
	PAID("결제완료"),
	PREPARING("배송준비"),
	SHIPPING("배송중"),
	CANCEL_REQUESTED("취소요청"),
	CANCELED("취소완료"),
	DELIVERED("배송완료");

	private final String text;

	OrderStatus(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}
}
