package com.commerce.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderProductResponseDTO {
	private String productName;
	private int quantity;
	private int price;
	private String mainIMageURL;

	public OrderProductResponseDTO(String productName, int quantity, int price, String mainIMageURL) {
		this.productName = productName;
		this.quantity = quantity;
		this.price = price;
		this.mainIMageURL = mainIMageURL;
	}
}
