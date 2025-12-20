package com.commerce.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaySuccessDTO {
	private String orderNumber;
	private int amount;       // finalPrice
	private String method;     // 결제수단
	private String approvedAt; // 화면 표시용 문자열( 2025-12-20 15:20:42)
}
