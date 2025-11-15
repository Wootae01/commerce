package com.commerce.mapper;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.commerce.domain.CartProduct;
import com.commerce.domain.Image;
import com.commerce.domain.Product;
import com.commerce.dto.CartProductDTO;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Component
public class CartProductMapper {

	private final ProductImageUtil productImageUtil;

	public List<CartProductDTO> toCartProductDTOs(List<CartProduct> cartProducts) {
		List<CartProductDTO> result = new ArrayList<>();
		for (CartProduct cartProduct : cartProducts) {
			result.add(toCartProductDTO(cartProduct));
		}
		return result;
	}

	public CartProductDTO toCartProductDTO(CartProduct cartProduct) {
		Product product = cartProduct.getProduct();

		CartProductDTO cartProductDTO = new CartProductDTO();
		cartProductDTO.setId(cartProduct.getId());
		cartProductDTO.setName(product.getName());
		cartProductDTO.setPrice(product.getPrice());
		cartProductDTO.setQuantity(cartProduct.getQuantity());
		cartProductDTO.setMainImageUrl(productImageUtil.getMainImageUrl(product));

		return cartProductDTO;
	}
}
