package com.commerce.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import com.commerce.domain.Cart;
import com.commerce.domain.CartProduct;
import com.commerce.dto.CartProductDTO;
import com.commerce.mapper.CartProductMapper;
import com.commerce.service.CartService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/cart")
public class CartController {

	private final CartService cartService;
	private final CartProductMapper cartProductMapper;

	@GetMapping
	public String viewCart(Model model) {
		Cart cart = cartService.getCart();
		List<CartProduct> cartProducts = cart.getCartProducts();
		List<CartProductDTO> dtos = cartProductMapper.toCartProductDTOs(cartProducts);

		int totalPrice = cartService.getTotalPrice(cart.getId());

		model.addAttribute("cartProducts", dtos);
		model.addAttribute("totalPrice", totalPrice);
		return "cart";
	}

	@PostMapping("/add/{cartProductId}")
	public String addProduct(@PathVariable Long cartProductId, int quantity,
		@RequestHeader(value = "Referer", required = false) String referer) {

		cartService.addCart(cartProductId, quantity);
		return "redirect:" + referer;
	}

	@PostMapping("/edit/{cartProductId}")
	public String editQuantity(@PathVariable Long cartProductId, int quantity) {
		cartService.addProductQuantity(cartProductId, quantity);

		return "redirect:/cart";
	}

	@PostMapping("/select/{cartProductId}")
	public String updateSelection(@PathVariable Long cartProductId, boolean checked) {
		cartService.updateSelection(cartProductId, checked);
		return "redirect:/cart";
	}


	@PostMapping("/delete/{cartProductId}")
	public String deleteCart(@PathVariable Long cartProductId) {
		cartService.deleteProduct(cartProductId);
		return "redirect:/cart";
	}

}
