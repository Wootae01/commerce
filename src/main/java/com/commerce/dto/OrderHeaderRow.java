package com.commerce.dto;

import java.time.LocalDateTime;

import com.commerce.domain.enums.OrderStatus;

public record OrderHeaderRow(
	Long orderId,
	String orderNumber,
	LocalDateTime orderDate,
	OrderStatus orderStatus,
	int finalPrice
) {}
