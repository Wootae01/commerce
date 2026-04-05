package com.commerce.order.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderProductResponseDTO {
	private Long id;
	private String productName;
	private String optionName;
	private int quantity;
	private int price;
	private String mainImageURL;

	public OrderProductResponseDTO(Long id, String productName, String optionName, int quantity, int price, String mainImageURL) {
		this.id = id;
		this.productName = productName;
		this.optionName = optionName;
		this.quantity = quantity;
		this.price = price;
		this.mainImageURL = mainImageURL;
	}
}
