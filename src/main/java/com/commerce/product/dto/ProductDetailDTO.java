package com.commerce.product.dto;

import java.util.List;

import com.commerce.admin.dto.ProductOptionDTO;
import lombok.Getter;

@Getter
public class ProductDetailDTO {
	private Long id;
	private int price;
	private String name;
	private String mainImageUrl;
	private List<String> images;
	private String description;
	private List<ProductOptionDTO> options;

	public ProductDetailDTO(Long id, int price, String name, String mainImageUrl, List<String> images,
		String description, List<ProductOptionDTO> options) {
		this.id = id;
		this.price = price;
		this.name = name;
		this.mainImageUrl = mainImageUrl;
		this.images = images;
		this.description = description;
		this.options = options;
	}

}
