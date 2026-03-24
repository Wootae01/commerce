package com.commerce.common.controller;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.commerce.cart.domain.Cart;
import com.commerce.cart.repository.CartProductRepository;
import com.commerce.cart.service.CartService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@Profile({"dev", "local"})
public class TestSupportController {
	private final CartProductRepository cartProductRepository;
	private final CartService cartService;

	//  k6 테스트용
	@GetMapping("/test/api/cart/cart-product-ids")
	@ResponseBody
	public List<Long> myCartProductIds() {
		Cart cart = cartService.getCart(); // 현재 유저 카트
		return cartProductRepository.findIdsByCartId(cart.getId());
	}
}
