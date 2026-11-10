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

	// 주문서(결제 전) 금액. 주문을 만들 때와 같은 계산이라야 주문서의 결제 금액이 결제창 금액과 같다
	public static OrderPriceDTO forCheckout(int productsTotal) {
		return of(productsTotal, OrderAmount.forNewOrder(productsTotal));
	}
}
