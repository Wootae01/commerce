package com.commerce.cart.service;

import java.util.List;
import java.util.Optional;

import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.exception.ApiException;
import com.commerce.common.util.ProductImageUtil;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.service.ProductService;
import org.springframework.stereotype.Service;

import com.commerce.cart.domain.Cart;
import com.commerce.cart.domain.CartProduct;
import com.commerce.product.domain.Product;
import com.commerce.user.domain.User;
import com.commerce.cart.dto.CartProductDTO;
import com.commerce.order.dto.OrderItemDTO;
import com.commerce.cart.repository.CartProductRepository;
import com.commerce.cart.repository.CartRepository;
import com.commerce.common.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CartService {
	private final CartRepository cartRepository;
	private final CartProductRepository cartProductRepository;
	private final ProductService productService;
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
		return cartProductRepository.findCheckedByUser(user);
	}

	public void addCart(Long productId, Long productOptionId, int quantity) {
		Product product = productService.findById(productId);
		ProductOption productOption = productService.resolveOption(productId, productOptionId);

		User user = securityUtil.getCurrentUser();

		Cart cart = cartRepository.findByUser(user)
			.orElseGet(() -> new Cart(user));

		// 같은 옵션이면 담으려는 수량만큼 증가 (옵션은 한 상품에만 속한다)
		List<CartProduct> cartProducts = cartProductRepository.findByCartIdWithProductAndOption(cart.getId());
		for (CartProduct cartProduct : cartProducts) {
			if (cartProduct.getProductOption().getId().equals(productOption.getId())) {
				cartProduct.addQuantity(quantity);
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
		CartProduct cartProduct = findMyCartProduct(cartProductId);

		cartProduct.setQuantity(quantity);
		cartProductRepository.save(cartProduct);
	}

	public void updateSelection(Long cartProductId, boolean checked) {
		CartProduct cartProduct = findMyCartProduct(cartProductId);

		cartProduct.setIsChecked(checked);
		cartProductRepository.save(cartProduct);
	}

	public void deleteProduct(Long cartProductId) {
		CartProduct cartProduct = findMyCartProduct(cartProductId);

		cartProduct.softDelete(SecurityUtil.getCurrentUsername());
		cartProductRepository.save(cartProduct);
	}

	// 다른 사용자의 장바구니 상품 ID로 요청하는 경우를 막기 위해 본인 장바구니에서만 조회한다.
	// 존재 여부를 노출하지 않도록 남의 상품도 404로 응답한다.
	private CartProduct findMyCartProduct(Long cartProductId) {
		Long userId = securityUtil.getCurrentUser().getId();
		return cartProductRepository.findByIdAndUserId(cartProductId, userId)
			.orElseThrow(() -> new ApiException(GeneralResponseCode.CART_ITEM_NOT_FOUND));
	}

	public int getTotalPrice(Long cartId) {
		List<CartProduct> cartProducts = cartProductRepository.findByCartIdWithProductAndOption(cartId);
		return getTotalPrice(cartProducts);
	}

	public int getTotalPrice(List<CartProduct> cartProducts) {
		return cartProducts.stream()
				.filter(CartProduct::isChecked)
				.mapToInt(cp -> cp.getQuantity() * cp.getUnitPrice())
				.sum();
	}
}
