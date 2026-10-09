package com.commerce.cart.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commerce.cart.domain.Cart;
import com.commerce.cart.dto.CartAddRequest;
import com.commerce.cart.dto.CartResponse;
import com.commerce.cart.dto.CartUpdateRequest;
import com.commerce.cart.service.CartService;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.dto.GeneralResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cart")
public class CartApiController {

	private final CartService cartService;

	@GetMapping
	public ResponseEntity<GeneralResponse<CartResponse>> getCart() {
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, buildCartResponse());
	}

	// 같은 상품 + 같은 옵션이 이미 있으면 수량이 1 증가한다.
	@PostMapping("/items")
	public ResponseEntity<GeneralResponse<CartResponse>> addItem(@Valid @RequestBody CartAddRequest request) {
		cartService.addCart(request.productId(), request.optionId(), request.quantity());
		return GeneralResponse.toResponseEntity(GeneralResponseCode.CREATED, buildCartResponse());
	}

	@PatchMapping("/items/{cartProductId}")
	public ResponseEntity<GeneralResponse<CartResponse>> updateItem(@PathVariable Long cartProductId,
		@Valid @RequestBody CartUpdateRequest request) {

		if (request.quantity() != null) {
			cartService.addProductQuantity(cartProductId, request.quantity());
		}
		if (request.checked() != null) {
			cartService.updateSelection(cartProductId, request.checked());
		}
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, buildCartResponse());
	}

	@DeleteMapping("/items/{cartProductId}")
	public ResponseEntity<GeneralResponse<CartResponse>> deleteItem(@PathVariable Long cartProductId) {
		cartService.deleteProduct(cartProductId);
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, buildCartResponse());
	}

	// 변경 후 화면을 다시 그릴 수 있도록 장바구니 전체를 돌려준다.
	private CartResponse buildCartResponse() {
		Cart cart = cartService.getCart();
		return new CartResponse(cartService.getCartProductDTOS(cart.getId()), cartService.getTotalPrice(cart.getId()));
	}
}
