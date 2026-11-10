package com.commerce.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.commerce.common.enums.OrderType;
import com.commerce.order.domain.Orders;
import com.commerce.order.dto.OrderCreateRequestDTO;
import com.commerce.order.dto.OrderDetailResponseDTO;
import com.commerce.order.dto.OrderItemDTO;
import com.commerce.order.dto.OrderPriceDTO;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.user.domain.User;

/**
 * 주문 상세의 금액(상품 합계·배송비·결제 금액, 주문 상품의 단가·합계)은 주문할 때 저장한 값으로 보인다.
 * 예시 주문: 10,000원 상품의 L(+2,000원) 옵션 2개 → 단가 12,000, 상품 합계 24,000, 배송비 3,000, 결제 금액 27,000.
 */
class OrderDetailAmountTest extends PaymentIntegrationTestSupport {

	private Long shirtId;
	private Long largeOptionId;

	@BeforeEach
	void setUpShirt() {
		Product shirt = new Product();
		shirt.update(10_000, "반팔 티셔츠", "설명");
		ProductOption large = new ProductOption("L", INITIAL_STOCK, 2_000);
		shirt.addOption(large);
		shirtId = productRepository.save(shirt).getId();
		largeOptionId = large.getId();
	}

	@Test
	@DisplayName("결제 정보에 상품 합계·배송비가 따로 보이고, 최종 결제금액은 주문의 결제 금액과 같다")
	void paymentCardShowsProductsTotalDeliveryFeeAndFinalPrice() {
		// given
		Orders order = orderTwoLargeShirts();

		// when
		OrderPriceDTO price = detailOf(order).getOrderPrice();

		// then
		assertThat(price.getProductsTotal()).as("상품 합계").isEqualTo(24_000);
		assertThat(price.getDeliveryFee()).as("배송비").isEqualTo(3_000);
		assertThat(price.getFinalPrice()).as("최종 결제금액").isEqualTo(27_000);
		assertThat(order.getFinalPrice()).as("토스에 승인 요청할 결제 금액").isEqualTo(27_000);
	}

	@Test
	@DisplayName("주문 상품의 단가·합계는 주문할 때 값(옵션 추가금 포함)이고, 주문 뒤 상품 가격이 바뀌어도 그대로다")
	void orderItemsShowUnitPriceAtOrderTime() {
		// given
		Orders order = orderTwoLargeShirts();
		Product shirt = productRepository.findById(shirtId).orElseThrow();
		shirt.update(15_000, shirt.getName(), shirt.getDescription());
		productRepository.save(shirt);

		// when
		OrderDetailResponseDTO detail = detailOf(order);

		// then
		assertThat(detail.getOrderItems())
			.extracting(OrderItemDTO::getQuantity, OrderItemDTO::getUnitPrice, OrderItemDTO::getLineTotal)
			.containsExactly(tuple(2, 12_000, 24_000));
		assertThat(detail.getOrderPrice().getProductsTotal()).as("상품 합계").isEqualTo(24_000);
	}

	@Test
	@DisplayName("배송비가 지금 정책과 다른 예전 주문은 주문할 때의 배송비와 결제 금액이 보인다")
	void oldOrderShowsDeliveryFeeAtOrderTime() {
		// given - 배송비가 2,500원이던 때의 주문
		Orders order = orderTwoLargeShirts();
		jdbcTemplate.update("update orders set delivery_fee = ?, final_price = ? where order_number = ?",
			2_500, 26_500, order.getOrderNumber());

		// when
		OrderPriceDTO price = detailOf(order).getOrderPrice();

		// then
		assertThat(price.getProductsTotal()).as("상품 합계").isEqualTo(24_000);
		assertThat(price.getDeliveryFee()).as("배송비").isEqualTo(2_500);
		assertThat(price.getFinalPrice()).as("최종 결제금액").isEqualTo(26_500);
	}

	private Orders orderTwoLargeShirts() {
		OrderCreateRequestDTO dto = new OrderCreateRequestDTO();
		dto.setName("홍길동");
		dto.setPhone("01012345678");
		dto.setAddress("서울");
		dto.setAddressDetail("어딘가");
		dto.setOrderType(OrderType.BUY_NOW);
		dto.setProductId(shirtId);
		dto.setOptionId(largeOptionId);
		dto.setQuantity(2);
		return orderService.prepareOrder(dto);
	}

	private OrderDetailResponseDTO detailOf(Orders order) {
		User user = userRepository.findById(userId).orElseThrow();
		return orderService.getMyOrderDetail(order.getOrderNumber(), user);
	}
}
