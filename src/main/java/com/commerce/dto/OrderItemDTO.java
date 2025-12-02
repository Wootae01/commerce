package com.commerce.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderItemDTO {
	private Long id;
	private int quantity;
	private int price;
	private String mainImageUrl;
	private String name;



	public OrderItemDTO(Long id, int quantity, int price, String mainImageUrl, String name) {
		this.id = id;
		this.quantity = quantity;
		this.price = price;
		this.mainImageUrl = mainImageUrl;
		this.name = name;
	}
}
