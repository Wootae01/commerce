package com.commerce.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderProductResponseDTO {
	private Long id;
	private String productName;
	private int quantity;
	private int price;
	private String mainImageURL;

	public OrderProductResponseDTO(Long id, String productName, int quantity, int price, String mainImageURL) {
		this.id = id;
		this.productName = productName;
		this.quantity = quantity;
		this.price = price;
		this.mainImageURL = mainImageURL;
	}
}
