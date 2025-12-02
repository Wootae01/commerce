package com.commerce.dto;

import java.util.List;

import com.commerce.domain.enums.OrderType;
import com.commerce.domain.enums.PaymentType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderCreateRequestDTO {
    private String name;
    private String phone;
    private String address;
    private String addressDetail;
    private String requestNote;

    private PaymentType payment;   // CASH, CARD
    private OrderType orderType;   // CART, BUY_NOW

    // 장바구니 주문일 때만 사용
    private List<Long> cartProductIds;

    // 즉시 구매일 때만 사용
    private Long productId;
    private int quantity;
}
