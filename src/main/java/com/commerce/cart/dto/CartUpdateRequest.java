package com.commerce.cart.dto;

import jakarta.validation.constraints.Min;

/**
 * 수량과 선택 여부 중 보낸 값만 변경한다.
 */
public record CartUpdateRequest(
	@Min(1) Integer quantity,
	Boolean checked
) {
}
