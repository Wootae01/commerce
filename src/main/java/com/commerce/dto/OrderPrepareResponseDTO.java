package com.commerce.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderPrepareResponseDTO {
	private String orderId;
	private String orderName;
	private int amount;

	private String successUrl;
	private String failUrl;

	private String customerName;
	private String customerMobilePhone;
}
