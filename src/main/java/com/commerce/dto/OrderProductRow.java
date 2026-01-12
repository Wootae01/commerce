package com.commerce.dto;

public record OrderProductRow(
	Long orderId,
	Long productId,
	int price,
	int quantity
) {}

