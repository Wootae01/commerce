package com.commerce.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminProductListDTO {

	private Long id;
	private String name;
	private int price;
	private int stock;
	private String mainImageUrl;
	private LocalDateTime createdAt;

}
