package com.commerce.product.dto;

import com.commerce.admin.dto.ProductOptionDTO;
import org.hibernate.validator.constraints.Range;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;


import java.util.List;

@Getter
@Setter
public class ProductDTO {
	@NotNull
	@Range(min = 100, max = 1000000000)
	private int price;

	@NotNull
	@Range(min = 0, max = 999999)
	private int stock;

	@NotBlank
	private String name;

	List<ProductOptionDTO> productOptionDTOList;

	private String description;
}
