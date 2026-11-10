package com.commerce.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.commerce.cart.domain.CartProduct;
import com.commerce.cart.dto.CartProductDTO;
import com.commerce.cart.service.CartService;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.enums.OrderType;
import com.commerce.common.exception.ApiException;
import com.commerce.order.domain.Orders;
import com.commerce.order.dto.OrderCreateRequestDTO;
import com.commerce.order.dto.OrderDetailResponseDTO;
import com.commerce.order.dto.OrderItemDTO;
import com.commerce.order.dto.OrderPriceDTO;
import com.commerce.payment.service.PaymentTxService;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.user.domain.User;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 주문 만들기: 장바구니 주문과 바로구매가 주문 상품·금액·주문 이름을 같은 규칙으로 만든다.
 * 예시 상품: 반팔 티셔츠 10,000원의 L(+2,000원) → 단가 12,000 / 양말 3,000원 단품 → 단가 3,000. 배송비 3,000.
 */
class OrderPrepareTest extends PaymentIntegrationTestSupport {

	@Autowired CartService cartService;
	@Autowired TransactionTemplate transactionTemplate;
	@Autowired PaymentTxService paymentTxService;
	@Autowired ObjectMapper objectMapper;

	private Long shirtId;
	private Long largeOptionId;
	private Long socksId;
	private Long socksOptionId;

	@BeforeEach
	void setUpProducts() {
		Product shirt = new Product();
		shirt.update(10_000, "반팔 티셔츠", "설명");
		ProductOption large = new ProductOption("L", INITIAL_STOCK, 2_000);
		shirt.addOption(large);
		shirtId = productRepository.save(shirt).getId();
		largeOptionId = large.getId();

		Product socks = new Product();
		socks.update(3_000, "양말", "설명");
		ProductOption single = new ProductOption("단품", INITIAL_STOCK, 0);
		socks.addOption(single);
		socksId = productRepository.save(socks).getId();
		socksOptionId = single.getId();
	}

	@Test
	@DisplayName("장바구니 주문은 담은 상품마다 주문 상품 한 줄이 되고, 단가·상품 합계·결제 금액·주문 이름이 맞다")
	void cartOrder_eachCartProductBecomesOrderProduct() {
		// given - 티셔츠 L 2개, 양말 3개
		addToCart(shirtId, largeOptionId, 2);
		addToCart(socksId, socksOptionId, 3);

		// when
		Orders order = orderService.prepareOrderFromCart(cartOrderForm(allCartProductIds()));

		// then
		OrderDetailResponseDTO detail = detailOf(order);
		assertThat(detail.getOrderItems())
			.extracting(OrderItemDTO::getName, OrderItemDTO::getQuantity, OrderItemDTO::getUnitPrice,
				OrderItemDTO::getLineTotal)
			.containsExactlyInAnyOrder(
				tuple("반팔 티셔츠", 2, 12_000, 24_000),
				tuple("양말", 3, 3_000, 9_000));

		OrderPriceDTO price = detail.getOrderPrice();
		assertThat(price.getProductsTotal()).as("상품 합계").isEqualTo(33_000);
		assertThat(price.getDeliveryFee()).as("배송비").isEqualTo(3_000);
		assertThat(price.getFinalPrice()).as("결제 금액").isEqualTo(36_000);
		assertThat(order.getOrderName()).as("주문 이름").isEqualTo("반팔 티셔츠 외 1건");
	}

	@Test
	@DisplayName("바로구매는 고른 옵션이 주문 상품 한 줄이 되고, 주문 이름은 상품명이며 장바구니는 그대로다")
	void buyNow_chosenOptionBecomesSingleOrderProduct() {
		// given - 장바구니에 양말이 담겨 있다
		addToCart(socksId, socksOptionId, 1);

		// when - 티셔츠 L 2개 바로구매
		Orders order = orderService.prepareOrderFromBuyNow(buyNowForm(shirtId, largeOptionId, 2));

		// then
		OrderDetailResponseDTO detail = detailOf(order);
		assertThat(detail.getOrderItems())
			.extracting(OrderItemDTO::getName, OrderItemDTO::getQuantity, OrderItemDTO::getUnitPrice,
				OrderItemDTO::getLineTotal)
			.containsExactly(tuple("반팔 티셔츠", 2, 12_000, 24_000));
		assertThat(detail.getOrderPrice().getFinalPrice()).as("결제 금액").isEqualTo(27_000);
		assertThat(order.getOrderName()).as("주문 이름").isEqualTo("반팔 티셔츠");
		assertThat(allCartProductIds()).as("장바구니").hasSize(1);
	}

	@Test
	@DisplayName("장바구니 주문의 결제가 성공하면 주문한 상품만 장바구니에서 빠진다")
	void cartOrderPaid_onlyOrderedCartProductsRemoved() throws Exception {
		// given - 티셔츠·양말·상품1을 담고, 티셔츠·양말만 주문 → 결제 진행 중
		addToCart(shirtId, largeOptionId, 1);
		addToCart(socksId, socksOptionId, 1);
		List<Long> orderedIds = allCartProductIds();
		addToCart(productId, optionId, 1);

		Orders order = orderService.prepareOrderFromCart(cartOrderForm(orderedIds));
		paymentTxService.lockAndDeductStock(order.getOrderNumber(), "paymentKey");

		// when
		paymentTxService.applyPaymentSuccess(order.getOrderNumber(), objectMapper.readTree("""
			{ "status": "DONE", "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
			"""));

		// then
		User user = userRepository.findById(userId).orElseThrow();
		Long cartId = cartRepository.findByUser(user).orElseThrow().getId();
		assertThat(cartService.getCartProductDTOS(cartId)).extracting(CartProductDTO::getName)
			.containsExactly("상품1");
	}

	@ParameterizedTest
	@EnumSource(OrderType.class)
	@DisplayName("재고보다 많이 주문하면 거절하고 주문을 만들지 않는다")
	void outOfStock_rejected(OrderType orderType) {
		// given
		Runnable placeOrder = orderPlacer(orderType, shirtId, largeOptionId, INITIAL_STOCK + 1);

		// when & then
		assertThatThrownBy(placeOrder::run)
			.isInstanceOf(ApiException.class)
			.extracting("responseCode").isEqualTo(GeneralResponseCode.PRODUCT_OUT_OF_STOCK);
		assertThat(orderRepository.count()).as("주문").isZero();
	}

	@ParameterizedTest
	@EnumSource(OrderType.class)
	@DisplayName("판매 중지된 상품이면 거절하고 주문을 만들지 않는다")
	void deletedProduct_rejected(OrderType orderType) {
		// given - 장바구니 주문은 담은 뒤에 판매 중지된다
		Runnable placeOrder = orderPlacer(orderType, shirtId, largeOptionId, 1);
		transactionTemplate.executeWithoutResult(status ->
			productRepository.findById(shirtId).orElseThrow().softDelete("admin"));

		// when & then
		assertThatThrownBy(placeOrder::run)
			.isInstanceOf(ApiException.class)
			.extracting("responseCode").isEqualTo(GeneralResponseCode.PRODUCT_NOT_FOUND);
		assertThat(orderRepository.count()).as("주문").isZero();
	}

	@ParameterizedTest
	@EnumSource(OrderType.class)
	@DisplayName("판매 중지된 옵션이면 거절하고 주문을 만들지 않는다")
	void deletedOption_rejected(OrderType orderType) {
		// given - 장바구니 주문은 담은 뒤에 옵션만 판매 중지된다
		Runnable placeOrder = orderPlacer(orderType, shirtId, largeOptionId, 1);
		transactionTemplate.executeWithoutResult(status ->
			productOptionRepository.findById(largeOptionId).orElseThrow().softDelete("admin"));

		// when & then
		assertThatThrownBy(placeOrder::run)
			.isInstanceOf(ApiException.class)
			.extracting("responseCode").isEqualTo(GeneralResponseCode.PRODUCT_OPTION_NOT_FOUND);
		assertThat(orderRepository.count()).as("주문").isZero();
	}

	// 주문 방식별로 주문할 준비를 하고(장바구니 주문이면 지금 담는다), 실행하면 주문을 만드는 동작을 돌려준다
	private Runnable orderPlacer(OrderType orderType, Long productId, Long optionId, int quantity) {
		if (orderType == OrderType.BUY_NOW) {
			return () -> orderService.prepareOrderFromBuyNow(buyNowForm(productId, optionId, quantity));
		}
		addToCart(productId, optionId, quantity);
		List<Long> cartProductIds = allCartProductIds();
		return () -> orderService.prepareOrderFromCart(cartOrderForm(cartProductIds));
	}

	private OrderCreateRequestDTO buyNowForm(Long productId, Long optionId, int quantity) {
		OrderCreateRequestDTO dto = receiverForm();
		dto.setOrderType(OrderType.BUY_NOW);
		dto.setProductId(productId);
		dto.setOptionId(optionId);
		dto.setQuantity(quantity);
		return dto;
	}

	// 장바구니 담기는 실제 요청에서 open-in-view 세션 안에서 돈다. 테스트에서는 트랜잭션으로 같은 조건을 만든다
	private void addToCart(Long productId, Long optionId, int quantity) {
		transactionTemplate.executeWithoutResult(status -> cartService.addCart(productId, optionId, quantity));
	}

	private List<Long> allCartProductIds() {
		return cartProductRepository.findAll().stream().map(CartProduct::getId).toList();
	}

	private OrderCreateRequestDTO cartOrderForm(List<Long> cartProductIds) {
		OrderCreateRequestDTO dto = receiverForm();
		dto.setOrderType(OrderType.CART);
		dto.setCartProductIds(cartProductIds);
		return dto;
	}

	private OrderCreateRequestDTO receiverForm() {
		OrderCreateRequestDTO dto = new OrderCreateRequestDTO();
		dto.setName("홍길동");
		dto.setPhone("01012345678");
		dto.setAddress("서울");
		dto.setAddressDetail("어딘가");
		return dto;
	}

	private OrderDetailResponseDTO detailOf(Orders order) {
		User user = userRepository.findById(userId).orElseThrow();
		return orderService.getMyOrderDetail(order.getOrderNumber(), user);
	}
}
