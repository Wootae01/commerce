package com.commerce.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class OrderItemDTO {
	private Long id;
	private int quantity;
	private int unitPrice;
	private int totalPrice;
	private String mainImageUrl;
	private String name;
}
