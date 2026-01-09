package com.commerce.dto;

import java.time.LocalDateTime;

import com.commerce.domain.enums.OrderStatus;

public record OrderListRow(
	String orderNumber,
	LocalDateTime orderDate,
	OrderStatus orderStatus,
	int totalPrice,
	Long productId,
	String productName,
	int quantity,
	int price
) {}
