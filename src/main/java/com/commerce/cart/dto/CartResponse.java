package com.commerce.cart.dto;

import java.util.List;

public record CartResponse(
	List<CartProductDTO> items,
	int totalPrice   // 선택된 상품 합계 (배송비 제외)
) {
}
