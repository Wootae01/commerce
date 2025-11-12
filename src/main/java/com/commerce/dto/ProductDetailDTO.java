package com.commerce.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
public class ProductDetailDTO {
	private Long id;
	private int price;
	private String name;
	private String mainImageUrl;
	private List<String> images;
	private String description;

	public ProductDetailDTO(Long id, int price, String name, String mainImageUrl, List<String> images,
		String description) {
		this.id = id;
		this.price = price;
		this.name = name;
		this.mainImageUrl = mainImageUrl;
		this.images = images;
		this.description = description;
	}

}
