package com.commerce.service;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import com.commerce.domain.Cart;
import com.commerce.domain.CartProduct;
import com.commerce.domain.Product;
import com.commerce.domain.User;
import com.commerce.repository.CartProductRepository;
import com.commerce.repository.CartRepository;
import com.commerce.repository.ProductRepository;
import com.commerce.repository.UserRepository;
import com.commerce.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartService {
	private final CartRepository cartRepository;
	private final CartProductRepository cartProductRepository;
	private final ProductRepository productRepository;
	private final UserRepository userRepository;
	private final SecurityUtil securityUtil;

	public Cart getCart() {
		String username = securityUtil.getCurrentUserName();
		User user = userRepository.findByUsername(username)
			.orElseThrow(() -> new NoSuchElementException("등록된 사용자가 아닙니다."));

		Cart cart = cartRepository.findByUser(user)
			.orElseThrow(() -> new NoSuchElementException("등록된 상품이 없습니다."));

		return cart;
	}


	public void addCart(Long productId, int quantity) {
		Product product = productRepository.findById(productId)
			.orElseThrow(() -> new NoSuchElementException("해당 상품이 존재하지 않습니다."));

		User user = userRepository.findByUsername(securityUtil.getCurrentUserName())
			.orElseThrow(() -> new NoSuchElementException("등록된 사용자가 아닙니다."));

		Cart cart = cartRepository.findByUser(user)
			.orElseGet(() -> new Cart(user));

		// 이미 존재하는 상품은 수량만 증가
		List<CartProduct> cartProducts = cart.getCartProducts();
		for (CartProduct cartProduct : cartProducts) {
			if (cartProduct.getProduct().getId().equals(productId)) {
				cartProduct.addQuantity();
				cartRepository.save(cart);
				return;
			}
		}

		// 장바구니에 새 상품 등록
		CartProduct cartProduct = new CartProduct(cart, product, quantity, false);
		cart.addProduct(cartProduct);

		cartRepository.save(cart);
	}

	public void addProductQuantity(Long cartProductId, int quantity) {
		CartProduct cartProduct = cartProductRepository.findById(cartProductId)
			.orElseThrow();

		cartProduct.setQuantity(quantity);
		cartProductRepository.save(cartProduct);
	}

	public void deleteProduct(Long cartProductId) {
		CartProduct cartProduct = cartProductRepository.findById(cartProductId)
			.orElseThrow();

		cartProductRepository.delete(cartProduct);
	}

	public int getTotalPrice(Long cartId) {
		Cart cart = cartRepository.findById(cartId)
			.orElseThrow();

		return cart.getCartProducts().stream()
			.mapToInt(cp -> cp.getQuantity() * cp.getProduct().getPrice())
			.sum();
	}
}
