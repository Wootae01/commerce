package com.commerce.dto;

import java.time.LocalDateTime;

import com.commerce.domain.enums.OrderStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class AdminOrderListResponseDTO {
	private Long id;
	private String orderNumber;
	private LocalDateTime orderDate;
	private String buyerName;
	private String paymentType;
	private String orderPhone;
	private int totalPrice;
	private OrderStatus orderStatus;
}
