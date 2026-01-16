package com.commerce.domain.enums;

public enum PaymentType {
    CARD("카드"),
    VIRTUAL_ACCOUNT("가상계좌"),
    EASY_PAY("간편결제"),
    MOBILE_PHONE("휴대폰"),
    TRANSFER("계좌이체"),
    CULTURE_GIFT_CERTIFICATE("문화상품권"),
    BOOK_GIFT_CERTIFICATE("도서문화상품권"),
    GAME_GIFT_CERTIFICATE("게임문화상품권"),
    UNKNOWN("기타");

    private final String text;

    PaymentType(String text) {
        this.text = text;
    }

    public String getText() {
        if (text == null) {
            return UNKNOWN.text;
        }
        return this.text;
    }

    public static PaymentType fromTossMethod(String method) {

        if (method == null) return UNKNOWN;
        String m = method.trim();

        return switch (m) {
            case "카드" -> CARD;
            case "계좌이체" -> TRANSFER;
            case "가상계좌" -> VIRTUAL_ACCOUNT;
            case "간편결제" -> EASY_PAY;
            case "휴대폰" -> MOBILE_PHONE;
            case "문화상품권" -> CULTURE_GIFT_CERTIFICATE;
            case "도서문화상품권" -> BOOK_GIFT_CERTIFICATE;
            case "게임문화상품권" -> GAME_GIFT_CERTIFICATE;
            default -> UNKNOWN;
        };
    }
}
