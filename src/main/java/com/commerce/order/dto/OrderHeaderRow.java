package com.commerce.order.dto;

import java.time.LocalDateTime;

import com.commerce.common.enums.OrderStatus;

public record OrderHeaderRow(
	Long orderId,
	String orderNumber,
	LocalDateTime orderDate,
	OrderStatus orderStatus,
	int finalPrice
) {}
