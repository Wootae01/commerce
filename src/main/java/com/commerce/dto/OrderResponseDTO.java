package com.commerce.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.commerce.domain.enums.OrderStatus;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderResponseDTO {
	private String orderNumber;
	private LocalDateTime orderDate;
	private OrderStatus orderStatus;
	List<OrderProductResponseDTO> productDTOS;
	private int totalPrice;

	public OrderResponseDTO(String orderNumber, LocalDateTime orderDate, OrderStatus orderStatus,
		List<OrderProductResponseDTO> productDTOS, int totalPrice) {
		this.orderNumber = orderNumber;
		this.orderDate = orderDate;
		this.orderStatus = orderStatus;
		this.productDTOS = productDTOS;
		this.totalPrice = totalPrice;
	}
}
