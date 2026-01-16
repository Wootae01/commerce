package com.commerce.dto;

public record OrderItemRow(
	Long orderId,
	Long productId,
	String productName,
	int quantity,
	int price
) {}
