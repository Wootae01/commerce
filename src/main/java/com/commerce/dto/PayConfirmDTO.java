package com.commerce.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PayConfirmDTO {
	private String paymentKey;
	private String orderId;
	private int amount;
}
