package com.commerce.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CartProductDTO {
	private Long id;
	private boolean isChecked;
	private int quantity;
	private int price;
	private String mainImageUrl;
	private String name;
}
