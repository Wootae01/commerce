package com.commerce.controller;

import com.commerce.domain.Cart;
import com.commerce.dto.CartProductDTO;
import com.commerce.service.CartService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/cart")
public class CartController {

	private final CartService cartService;

	@GetMapping
	public String viewCart(Model model) {
		Cart cart = cartService.getCart();
		List<CartProductDTO> cartProductDTOS = cartService.getCartProductDTOS(cart.getId());

		int totalPrice = cartService.getTotalPrice(cart.getId());

		model.addAttribute("cartProducts", cartProductDTOS);
		model.addAttribute("totalPrice", totalPrice);
		return "cart";
	}

	@PostMapping("/add/{cartProductId}")
	public String addProduct(@PathVariable Long cartProductId, @Min(1) int quantity,
		@RequestHeader(value = "Referer", required = false) String referer) {

		cartService.addCart(cartProductId, quantity);
		return "redirect:" + referer;
	}

	@PostMapping("/edit/{cartProductId}")
	public String editQuantity(@PathVariable Long cartProductId, @Min(1) int quantity) {
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
