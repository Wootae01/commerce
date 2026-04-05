package com.commerce.order.dto;

public record OrderProductRow(
	Long orderId,
	Long productId,
	Long optionId,
	int price,
	int quantity
) {}
