package com.commerce.order.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.commerce.cart.domain.CartProduct;
import com.commerce.cart.service.CartService;
import com.commerce.common.enums.OrderType;
import com.commerce.order.dto.OrderCheckoutResponse;
import com.commerce.order.dto.OrderItemDTO;
import com.commerce.order.dto.OrderMapper;
import com.commerce.order.dto.OrderPriceDTO;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.service.ProductService;
import com.commerce.user.domain.User;

import lombok.RequiredArgsConstructor;

/**
 * 주문서 조회. 화면(/orders/*)과 API(/api/orders/checkout/*)가 함께 쓴다.
 * 주문서의 결제 금액이 토스 결제창 금액이 되므로, 주문서 내용은 이곳에서만 만든다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrderCheckoutService {

	private final CartService cartService;
	private final ProductService productService;
	private final OrderMapper orderMapper;

	@Value("${toss.payments.client-key}")
	private String tossClientKey;

	// 장바구니에서 선택한 상품으로 만든 주문서
	public OrderCheckoutResponse getCartCheckout(User user) {
		List<CartProduct> cartProducts = cartService.getSelectedProduct(user);
		List<OrderItemDTO> items = orderMapper.toOrderItemDTOFromCart(cartProducts);

		return checkout(user, OrderType.CART, items, cartService.getProductsTotal(cartProducts));
	}

	// 바로구매 주문서. 옵션을 고르지 않았으면 기본 옵션으로 산다
	public OrderCheckoutResponse getBuyNowCheckout(User user, Long productId, Long optionId, int quantity) {
		Product product = productService.findById(productId);
		ProductOption option = productService.resolveOption(productId, optionId);
		OrderItemDTO item = orderMapper.toOrderItemDTOFromCart(product, quantity, option);

		return checkout(user, OrderType.BUY_NOW, List.of(item), item.getLineTotal());
	}

	private OrderCheckoutResponse checkout(User user, OrderType orderType, List<OrderItemDTO> items,
		int productsTotal) {
		return new OrderCheckoutResponse(orderType, items, OrderCheckoutResponse.Orderer.from(user),
			OrderPriceDTO.forCheckout(productsTotal), tossClientKey);
	}
}
