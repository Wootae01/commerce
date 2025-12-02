package com.commerce.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderPriceDTO {
	private int totalPrice;
	private int deliveryFee;
	private int finalPrice;

	public OrderPriceDTO(int totalPrice, int deliveryFee, int finalPrice) {
		this.totalPrice = totalPrice;
		this.deliveryFee = deliveryFee;
		this.finalPrice = finalPrice;
	}
}
