package com.commerce.product.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.commerce.admin.dto.ProductOptionDTO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductResponseDTO {
	private Long id;
	private String name;
	private String description;
	private int price;
	private int stock;
	List<ProductOptionDTO> productOptionDTOList;
	private String mainImageUrl;
	private List<ImageResponseDTO> images;
	private LocalDateTime createdAt;

}
