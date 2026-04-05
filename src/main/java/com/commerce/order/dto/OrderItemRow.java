package com.commerce.order.dto;

public record OrderItemRow(
	Long orderId,
	Long productId,
	String productName,
	String optionName,
	int quantity,
	int price
) {}
