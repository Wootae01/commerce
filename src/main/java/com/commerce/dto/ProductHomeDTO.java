package com.commerce.dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductHomeDTO {
	private Long id;
	private String mainImageUrl;
	private String name;
	private int price;

	public ProductHomeDTO(Long id, String mainImageUrl, String name, int price) {
		this.id = id;
		this.mainImageUrl = mainImageUrl;
		this.name = name;
		this.price = price;
	}
}
