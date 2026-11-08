package com.commerce.order.dto;

import com.commerce.order.domain.OrderAmount;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderPriceDTO {
	private int productsTotal;
	private int deliveryFee;
	private int finalPrice;

	public OrderPriceDTO(int productsTotal, int deliveryFee, int finalPrice) {
		this.productsTotal = productsTotal;
		this.deliveryFee = deliveryFee;
		this.finalPrice = finalPrice;
	}

	public static OrderPriceDTO of(int productsTotal, OrderAmount amount) {
		return new OrderPriceDTO(productsTotal, amount.deliveryFee(), amount.finalPrice());
	}
}
