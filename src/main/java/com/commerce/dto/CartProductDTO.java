package com.commerce.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CartProductDTO {
	private Long id; 			// cartProduct id
	private boolean isChecked;
	private int quantity;
	private int price;
	private String mainImageUrl;
	private String name;
}
