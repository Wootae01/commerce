package com.commerce.dto;

import java.util.List;

import com.commerce.domain.enums.OrderType;
import com.commerce.domain.enums.PaymentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderCreateRequestDTO {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern( regexp = "^010-\\d{4}-\\d{4}$")
    private String phone;

    @NotBlank
    private String address;

    @NotBlank
    private String addressDetail;

    private String requestNote;

    @NotNull
    private PaymentType payment;   // CASH, CARD

    @NotNull
    private OrderType orderType;   // CART, BUY_NOW

    // 장바구니 주문일 때만 사용
    private List<Long> cartProductIds;

    // 즉시 구매일 때만 사용
    private Long productId;
    private int quantity;
}
