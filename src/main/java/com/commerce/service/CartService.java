package com.commerce.service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.commerce.util.ProductImageUtil;
import org.springframework.stereotype.Service;

import com.commerce.domain.Cart;
import com.commerce.domain.CartProduct;
import com.commerce.domain.Product;
import com.commerce.domain.User;
import com.commerce.dto.CartProductDTO;
import com.commerce.dto.OrderItemDTO;
import com.commerce.repository.CartProductRepository;
import com.commerce.repository.CartRepository;
import com.commerce.repository.ProductRepository;
import com.commerce.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartService {
	private final CartRepository cartRepository;
	private final CartProductRepository cartProductRepository;
	private final ProductRepository productRepository;
	private final SecurityUtil securityUtil;
	private final ProductImageUtil productImageUtil;

	public List<CartProduct> findAllByIdWithProduct(List<Long> cartProductIds) {
		return cartProductRepository.findAllByIdWithProduct(cartProductIds);
	}

	public List<OrderItemDTO> getOrderItemDTOS(List<Long> cartProductIds) {
		List<OrderItemDTO> result = cartProductRepository.findOrderItemDTO(cartProductIds);
		for (OrderItemDTO dto : result) {
			String imageUrl = productImageUtil.getImageUrl(dto.getMainImageUrl());
			dto.setMainImageUrl(imageUrl);
		}
		return result;
	}

	public List<CartProductDTO> getCartProductDTOS(Long cartId) {
		List<CartProductDTO> cartRows = cartProductRepository.findCartRows(cartId);
		for (CartProductDTO cartRow : cartRows) {

			String imageUrl = productImageUtil.getImageUrl(cartRow.getMainImageUrl());
			cartRow.setMainImageUrl(imageUrl);
		}
		return cartRows;
	}

	public Cart getCart() {
		User user = securityUtil.getCurrentUser();

		Optional<Cart> optional = cartRepository.findByUser(user);
		// 카트 존재하지 않으면 새로 생성
		if (optional.isEmpty()) {
			Cart cart = new Cart(user);

			return cartRepository.save(cart);
		} else {
			return optional.get();
		}
	}

	public List<CartProduct> getSelectedProduct() {

		User user = securityUtil.getCurrentUser();
		Cart cart = cartRepository.findByUser(user)
				.orElseThrow(() -> new NoSuchElementException("카트가 존재하지 않습니다."));

		List<CartProduct> cartProducts = cart.getCartProducts();

        return cartProducts.stream()
                .filter(CartProduct::isChecked)
                .toList();
	}

	public void addCart(Long productId, int quantity) {
		Product product = productRepository.findById(productId)
			.orElseThrow(() -> new NoSuchElementException("해당 상품이 존재하지 않습니다."));

		User user = securityUtil.getCurrentUser();

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

	public void updateSelection(Long cartProductId, boolean checked) {
		CartProduct cartProduct = cartProductRepository.findById(cartProductId)
			.orElseThrow();

		cartProduct.setIsChecked(checked);
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
			.filter(CartProduct::isChecked)
			.mapToInt(cp -> cp.getQuantity() * cp.getProduct().getPrice())
			.sum();
	}

	public int getTotalPrice(List<CartProduct> cartProducts) {
		return cartProducts.stream()
				.filter(CartProduct::isChecked)
				.mapToInt(cp -> cp.getQuantity() * cp.getProduct().getPrice())
				.sum();
	}
}
