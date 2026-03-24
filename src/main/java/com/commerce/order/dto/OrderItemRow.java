package com.commerce.order.dto;

public record OrderItemRow(
	Long orderId,
	Long productId,
	String productName,
	int quantity,
	int price
) {}
