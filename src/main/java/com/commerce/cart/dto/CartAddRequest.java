package com.commerce.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartAddRequest(
	@NotNull Long productId,
	Long optionId,
	@NotNull @Min(1) Integer quantity
) {
}
