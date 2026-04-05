package com.commerce.cart.service;

import java.util.List;
import java.util.Optional;

import com.commerce.common.exception.EntityNotFoundException;
import com.commerce.common.util.ProductImageUtil;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.repository.ProductOptionRepository;
import org.springframework.stereotype.Service;

import com.commerce.cart.domain.Cart;
import com.commerce.cart.domain.CartProduct;
import com.commerce.product.domain.Product;
import com.commerce.user.domain.User;
import com.commerce.cart.dto.CartProductDTO;
import com.commerce.order.dto.OrderItemDTO;
import com.commerce.cart.repository.CartProductRepository;
import com.commerce.cart.repository.CartRepository;
import com.commerce.product.repository.ProductRepository;
import com.commerce.common.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartService {
	private final CartRepository cartRepository;
	private final CartProductRepository cartProductRepository;
	private final ProductRepository productRepository;
	private final SecurityUtil securityUtil;
	private final ProductImageUtil productImageUtil;
	private final ProductOptionRepository productOptionRepository;

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
		return cartProductRepository.findCheckedByUser(user);
	}

	public void addCart(Long productId, Long productOptionId, int quantity) {
		Product product = productRepository.findById(productId)
			.orElseThrow(() -> new EntityNotFoundException("해당 상품이 존재하지 않습니다."));

		ProductOption productOption = null;
		if (productOptionId != null) {
			productOption = productOptionRepository.findById(productOptionId)
					.orElseThrow(() -> new EntityNotFoundException("해당 옵션이 존재하지 않습니다."));
		}

		User user = securityUtil.getCurrentUser();

		Cart cart = cartRepository.findByUser(user)
			.orElseGet(() -> new Cart(user));

		// 같은 상품 + 같은 옵션이면 수량만 증가
		List<CartProduct> cartProducts = cartProductRepository.findByCartIdWithProductAndOption(cart.getId());
		for (CartProduct cartProduct : cartProducts) {
			boolean sameProduct = cartProduct.getProduct().getId().equals(productId);
			boolean sameOption = (cartProduct.getProductOption() == null && productOptionId == null)
					|| (cartProduct.getProductOption() != null && cartProduct.getProductOption().getId().equals(productOptionId));
			if (sameProduct && sameOption) {
				cartProduct.addQuantity();
				cartProductRepository.save(cartProduct);
				return;
			}
		}

		// 장바구니에 새 상품 등록
		CartProduct cartProduct = new CartProduct(cart, product, productOption, quantity, false);
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
		List<CartProduct> cartProducts = cartProductRepository.findByCartIdWithProductAndOption(cartId);
		return cartProducts.stream()
			.filter(CartProduct::isChecked)
			.mapToInt(cp -> {
				int additionalPrice = cp.getProductOption() != null ? cp.getProductOption().getAdditionalPrice() : 0;
				return cp.getQuantity() * (cp.getProduct().getPrice() + additionalPrice);
			})
			.sum();
	}

	public int getTotalPrice(List<CartProduct> cartProducts) {
		return cartProducts.stream()
				.filter(CartProduct::isChecked)
				.mapToInt(cp -> {
					int additionalPrice = cp.getProductOption() != null ? cp.getProductOption().getAdditionalPrice() : 0;
					return cp.getQuantity() * (cp.getProduct().getPrice() + additionalPrice);
				})
				.sum();
	}
}
