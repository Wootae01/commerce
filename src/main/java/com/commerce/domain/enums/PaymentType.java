package com.commerce.domain.enums;

import lombok.Getter;

@Getter
public enum PaymentType {
    CASH("무통장 입금"),
    CARD("카드 결제");

    private final String text;

    PaymentType(String text) {
        this.text = text;
    }
}
