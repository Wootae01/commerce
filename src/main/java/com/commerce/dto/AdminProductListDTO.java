package com.commerce.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminProductListDTO {

	private Long id;
	private String name;
	private int price;
	private int stock;
	private String mainImageUrl;
	private LocalDateTime createdAt;

	private boolean featured;
	private Integer featuredRank;
}
