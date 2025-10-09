package com.commerce.domain.enums;

public enum DeliveryStatus {
    PENDING("결제 대기"),
    PAID("결제 완료"),
    PROCESSING("상품 준비 중"),
    SHIPPED("배송 중"),
    DELIVERED("배송 완료"),
    CANCELLED("주문 취소"),
    RETURNED("반품 완료");

    private final String description;

    DeliveryStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
