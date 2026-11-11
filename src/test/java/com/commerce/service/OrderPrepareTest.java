package com.commerce.service;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.assertj.core.groups.Tuple;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.ModelAndView;

import com.commerce.cart.domain.CartProduct;
import com.commerce.cart.dto.CartProductDTO;
import com.commerce.cart.service.CartService;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.enums.OrderType;
import com.commerce.common.exception.ApiException;
import com.commerce.order.domain.OrderProduct;
import com.commerce.order.domain.Orders;
import com.commerce.order.dto.OrderCreateRequestDTO;
import com.commerce.order.dto.OrderDetailResponseDTO;
import com.commerce.order.dto.OrderItemDTO;
import com.commerce.order.dto.OrderPriceDTO;
import com.commerce.payment.external.TossPaymentResponse;
import com.commerce.payment.service.PaymentTxService;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.user.domain.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 주문 만들기: 장바구니 주문과 바로구매가 주문 상품·금액·주문 이름을 같은 규칙으로 만든다.
 * 주문서 조회: 주문서(API·화면)가 보여 주는 단가·금액이 주문을 만들 때와 같다.
 * 예시 상품: 반팔 티셔츠 10,000원의 L(+2,000원) → 단가 12,000 / 양말 3,000원 단품 → 단가 3,000. 배송비 3,000.
 */
class OrderPrepareTest extends PaymentIntegrationTestSupport {

	@Autowired CartService cartService;
	@Autowired TransactionTemplate transactionTemplate;
	@Autowired PaymentTxService paymentTxService;
	@Autowired ObjectMapper objectMapper;
	@Autowired WebApplicationContext context;

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
		Orders order = orderService.prepareOrder(cartOrderForm(allCartProductIds()));

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
		Orders order = orderService.prepareOrder(buyNowForm(shirtId, largeOptionId, 2));

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

		Orders order = orderService.prepareOrder(cartOrderForm(orderedIds));
		paymentTxService.lockAndDeductStock(order.getOrderNumber(), "paymentKey");

		// when
		paymentTxService.applyPaymentSuccess(order.getOrderNumber(), objectMapper.readValue("""
			{ "status": "DONE", "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
			""", TossPaymentResponse.class));

		// then
		User user = userRepository.findById(userId).orElseThrow();
		Long cartId = cartRepository.findByUser(user).orElseThrow().getId();
		assertThat(cartService.getCartProductDTOS(cartId)).extracting(CartProductDTO::getName)
			.containsExactly("상품1");
	}

	@Test
	@DisplayName("주문 상품은 담은 순서와 상관없이 옵션 id 오름차순으로 들어간다(결제 재고 차감과 같은 락 순서)")
	void orderProductsInsertedInOptionIdOrder() {
		// given - 티셔츠에 나중에 추가한 M 옵션(가장 큰 옵션 id)을 먼저 담고, 양말을 나중에 담는다
		// 장바구니 상품 id 순서로도, 상품 id 순서로도 M이 앞이라 옵션 id 순서와 어긋난다
		Long mediumOptionId = transactionTemplate.execute(status -> {
			ProductOption medium = new ProductOption("M", INITIAL_STOCK, 1_000);
			productRepository.findById(shirtId).orElseThrow().addOption(medium);
			productOptionRepository.flush();
			return medium.getId();
		});
		assertThat(socksOptionId).isLessThan(mediumOptionId);
		addToCart(shirtId, mediumOptionId, 1);
		addToCart(socksId, socksOptionId, 1);

		// when
		Orders order = orderService.prepareOrder(cartOrderForm(allCartProductIds()));

		// then - 주문 상품 행을 넣은 순서(id 순)의 옵션 = FK 확인으로 옵션 S락을 잡는 순서
		assertThat(orderProductRepository.findOrderProductByOrderIdWithProduct(order.getId()).stream()
			.sorted(Comparator.comparing(OrderProduct::getId))
			.map(op -> op.getProductOption().getId()))
			.containsExactly(socksOptionId, mediumOptionId);
	}

	@ParameterizedTest
	@CsvSource({
		"CART, 36000, 반팔 티셔츠 외 1건",
		"BUY_NOW, 27000, 반팔 티셔츠"
	})
	@DisplayName("주문서 화면의 결제하기(/pay/prepare, 폼)는 주문 방식대로 주문을 만들고 결제창에 쓸 금액·주문 이름을 준다")
	void payPrepareForm_createsOrderByOrderType(OrderType orderType, int amount, String orderName) throws Exception {
		// given
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post("/pay/prepare")
			.with(user("username").roles("USER"))
			.with(csrf())
			.param("name", "홍길동")
			.param("phone", "01012345678")
			.param("address", "서울")
			.param("addressDetail", "어딘가")
			.param("orderType", orderType.name());
		if (orderType == OrderType.CART) {
			addToCart(shirtId, largeOptionId, 2);
			addToCart(socksId, socksOptionId, 3);
			request.param("cartProductIds", allCartProductIds().stream().map(String::valueOf).toArray(String[]::new));
		} else {
			request.param("productId", shirtId.toString())
				.param("optionId", largeOptionId.toString())
				.param("quantity", "2");
		}

		// when & then
		mockMvc().perform(request)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(amount))
			.andExpect(jsonPath("$.orderName").value(orderName));
		assertThat(orderRepository.count()).as("주문").isEqualTo(1);
	}

	@ParameterizedTest
	@CsvSource({
		"CART, 36000, 반팔 티셔츠 외 1건",
		"BUY_NOW, 27000, 반팔 티셔츠"
	})
	@DisplayName("주문 생성 API(POST /api/orders, JSON)는 주문 방식대로 주문을 만들고 결제창에 쓸 금액·주문 이름을 준다")
	void createOrderApi_createsOrderByOrderType(OrderType orderType, int amount, String orderName) throws Exception {
		// given
		OrderCreateRequestDTO dto;
		if (orderType == OrderType.CART) {
			addToCart(shirtId, largeOptionId, 2);
			addToCart(socksId, socksOptionId, 3);
			dto = cartOrderForm(allCartProductIds());
		} else {
			dto = buyNowForm(shirtId, largeOptionId, 2);
		}

		// when & then
		mockMvc().perform(MockMvcRequestBuilders.post("/api/orders")
				.with(user("username").roles("USER"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(dto)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.amount").value(amount))
			.andExpect(jsonPath("$.data.orderName").value(orderName));
		assertThat(orderRepository.count()).as("주문").isEqualTo(1);
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

	@ParameterizedTest
	@ValueSource(strings = {"/api/orders/checkout/cart", "/orders/cart"})
	@DisplayName("장바구니 주문서(API·화면)는 선택한 상품의 단가·상품별 합계와 상품 합계·배송비·결제 금액을 보여준다")
	void cartCheckout_showsItemsAndAmounts(String url) throws Exception {
		// given - 티셔츠 L 2개, 양말 3개를 담고 선택
		addToCart(shirtId, largeOptionId, 2);
		addToCart(socksId, socksOptionId, 3);
		selectAllCartProducts();

		// when
		JsonNode checkout = checkout(MockMvcRequestBuilders.get(url));

		// then
		assertThat(items(checkout)).containsExactlyInAnyOrder(
			tuple("반팔 티셔츠", 2, 12_000, 24_000),
			tuple("양말", 3, 3_000, 9_000));
		assertThat(checkout.at("/orderPrice/productsTotal").asInt()).as("상품 합계").isEqualTo(33_000);
		assertThat(checkout.at("/orderPrice/deliveryFee").asInt()).as("배송비").isEqualTo(3_000);
		assertThat(checkout.at("/orderPrice/finalPrice").asInt()).as("결제 금액").isEqualTo(36_000);
	}

	@ParameterizedTest
	@ValueSource(strings = {"/api/orders/checkout/buy-now", "/orders/buy-now"})
	@DisplayName("바로구매 주문서(API·화면)는 옵션 추가금을 더한 단가로 상품별 합계와 상품 합계·배송비·결제 금액을 보여준다")
	void buyNowCheckout_showsItemAndAmounts(String url) throws Exception {
		// when - 티셔츠 L 2개
		JsonNode checkout = checkout(MockMvcRequestBuilders.get(url)
			.param("productId", shirtId.toString())
			.param("optionId", largeOptionId.toString())
			.param("quantity", "2"));

		// then
		assertThat(items(checkout)).containsExactly(tuple("반팔 티셔츠", 2, 12_000, 24_000));
		assertThat(checkout.at("/orderPrice/productsTotal").asInt()).as("상품 합계").isEqualTo(24_000);
		assertThat(checkout.at("/orderPrice/deliveryFee").asInt()).as("배송비").isEqualTo(3_000);
		assertThat(checkout.at("/orderPrice/finalPrice").asInt()).as("결제 금액").isEqualTo(27_000);
	}

	@ParameterizedTest
	@EnumSource(OrderType.class)
	@DisplayName("주문서의 결제 금액으로 주문이 만들어진다(다르면 결제 확정 때 금액 불일치로 거절된다)")
	void checkoutFinalPrice_equalsCreatedOrderAmount(OrderType orderType) throws Exception {
		// given - 주문서를 조회한 뒤 그 주문서로 결제하기
		MockHttpServletRequestBuilder checkoutRequest;
		OrderCreateRequestDTO orderForm;
		if (orderType == OrderType.CART) {
			addToCart(shirtId, largeOptionId, 2);
			addToCart(socksId, socksOptionId, 3);
			selectAllCartProducts();
			checkoutRequest = MockMvcRequestBuilders.get("/api/orders/checkout/cart");
			orderForm = cartOrderForm(allCartProductIds());
		} else {
			checkoutRequest = MockMvcRequestBuilders.get("/api/orders/checkout/buy-now")
				.param("productId", shirtId.toString())
				.param("optionId", largeOptionId.toString())
				.param("quantity", "2");
			orderForm = buyNowForm(shirtId, largeOptionId, 2);
		}
		int checkoutFinalPrice = checkout(checkoutRequest).at("/orderPrice/finalPrice").asInt();

		// when
		String created = mockMvc().perform(MockMvcRequestBuilders.post("/api/orders")
				.with(user("username").roles("USER"))
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(orderForm)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

		// then - 결제창 금액 = 주문에 저장된 결제 금액
		assertThat(objectMapper.readTree(created).at("/data/amount").asInt()).isEqualTo(checkoutFinalPrice);
	}

	// 주문서 조회. API는 응답 JSON의 data를, 화면은 모델의 주문 상품·금액을 같은 모양의 JSON으로 돌려준다
	private JsonNode checkout(MockHttpServletRequestBuilder request) throws Exception {
		MvcResult result = mockMvc().perform(request.with(user("username").roles("USER")))
			.andExpect(status().isOk())
			.andReturn();

		ModelAndView page = result.getModelAndView();
		if (page == null) {
			return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
		}
		return objectMapper.valueToTree(Map.of(
			"items", page.getModel().get("orderItems"),
			"orderPrice", page.getModel().get("orderPrice")));
	}

	// 주문서의 주문 상품: (상품명, 수량, 단가, 상품별 합계)
	private static List<Tuple> items(JsonNode checkout) {
		List<Tuple> items = new ArrayList<>();
		for (JsonNode item : checkout.get("items")) {
			items.add(tuple(item.get("name").asText(), item.get("quantity").asInt(), item.get("unitPrice").asInt(),
				item.get("lineTotal").asInt()));
		}
		return items;
	}

	// 주문서에는 장바구니에서 선택한 상품만 나온다. 담기만 하면 선택 안 된 상태다
	private void selectAllCartProducts() {
		for (Long cartProductId : allCartProductIds()) {
			transactionTemplate.executeWithoutResult(status -> cartService.updateSelection(cartProductId, true));
		}
	}

	@Test
	@DisplayName("장바구니 주문은 담은 상품마다 판매 중지 → 재고 순서로 확인해, 앞 상품의 거절 이유로 거절한다")
	void cartOrder_rejectedByFirstFailingCartProduct() {
		// given - 티셔츠 L을 재고보다 많이 담고, 그 뒤에 담은 양말은 판매 중지된다
		addToCart(shirtId, largeOptionId, INITIAL_STOCK + 1);
		addToCart(socksId, socksOptionId, 1);
		List<Long> cartProductIds = allCartProductIds();
		transactionTemplate.executeWithoutResult(status ->
			productRepository.findById(socksId).orElseThrow().softDelete("admin"));

		// when & then - 앞에 담은 티셔츠의 재고 부족이 거절 이유다
		assertThatThrownBy(() -> orderService.prepareOrder(cartOrderForm(cartProductIds)))
			.isInstanceOf(ApiException.class)
			.extracting("responseCode").isEqualTo(GeneralResponseCode.PRODUCT_OUT_OF_STOCK);
		assertThat(orderRepository.count()).as("주문").isZero();
	}

	// 주문 방식별로 주문할 준비를 하고(장바구니 주문이면 지금 담는다), 실행하면 주문을 만드는 동작을 돌려준다
	private Runnable orderPlacer(OrderType orderType, Long productId, Long optionId, int quantity) {
		if (orderType == OrderType.BUY_NOW) {
			return () -> orderService.prepareOrder(buyNowForm(productId, optionId, quantity));
		}
		addToCart(productId, optionId, quantity);
		List<Long> cartProductIds = allCartProductIds();
		return () -> orderService.prepareOrder(cartOrderForm(cartProductIds));
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

	// MockMvc는 직접 만든다. @AutoConfigureMockMvc를 쓰면 컨텍스트가 하나 더 생겨 테스트 MySQL 커넥션 한도를 넘는다
	private MockMvc mockMvc() {
		return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
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
