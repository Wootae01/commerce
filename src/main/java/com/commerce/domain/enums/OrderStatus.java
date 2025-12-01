package com.commerce.domain.enums;

public enum OrderStatus {
	WAITING_FOR_PAYMENT("결제 대기"),
	WAITING_FOR_DEPOSIT("무통장 입금 대기"),
	PAID("결제 완료"),
	CANCELLED("주문 취소"),
	SHIPPING("배송 중"),
	DELIVERED("배송 완료"),
	COMPLETED("구매 확정");

	private final String text;

	OrderStatus(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}
}
