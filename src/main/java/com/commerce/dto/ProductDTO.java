package com.commerce.dto;

import org.hibernate.validator.constraints.Range;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductDTO {
	@NotNull
	@Range(min = 100, max = 1000000000)
	private int price;

	@NotBlank
	private String name;

	@NotNull
	private int stock;

	private String description;
}
