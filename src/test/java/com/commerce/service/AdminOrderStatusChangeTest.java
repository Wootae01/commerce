package com.commerce.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.RoleType;
import com.commerce.common.exception.ApiException;
import com.commerce.config.IntegrationTest;
import com.commerce.order.domain.Orders;
import com.commerce.order.repository.OrderRepository;
import com.commerce.order.service.OrderService;
import com.commerce.user.domain.User;
import com.commerce.user.repository.UserRepository;

/**
 * 관리자 주문 상태 일괄 변경 테스트
 * 배송 흐름(PAID → PREPARING → SHIPPING → DELIVERED)으로 한 단계씩만 바꿀 수 있고,
 * 허용되지 않은 전이가 하나라도 섞이면 아무 주문도 바꾸지 않는다.
 */
@IntegrationTest
class AdminOrderStatusChangeTest {

	@Autowired OrderService orderService;
	@Autowired OrderRepository orderRepository;
	@Autowired UserRepository userRepository;

	private User user;
	private int orderSeq;

	@BeforeEach
	void setUp() {
		user = userRepository.save(User.builder()
			.customerPaymentKey("customerPaymentKey")
			.email("c@naver.com")
			.name("홍길동")
			.phone("01012345678")
			.role(RoleType.ROLE_USER)
			.username("username")
			.build());
	}

	@AfterEach
	void cleanUp() {
		orderRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("허용되지 않은 전이가 섞이면 ORDER_STATUS_CHANGE_NOT_ALLOWED 예외가 발생하고 모든 주문이 그대로다")
	void mixedBatch_rejectedAndNothingChanged() {
		Orders paid = saveOrder(OrderStatus.PAID);
		Orders canceled = saveOrder(OrderStatus.CANCELED);

		assertThatThrownBy(() -> orderService.changeStatus(List.of(paid.getId(), canceled.getId()), OrderStatus.PREPARING))
			.isInstanceOf(ApiException.class)
			.extracting("responseCode").isEqualTo(GeneralResponseCode.ORDER_STATUS_CHANGE_NOT_ALLOWED);

		assertThat(statusOf(paid)).isEqualTo(OrderStatus.PAID);
		assertThat(statusOf(canceled)).isEqualTo(OrderStatus.CANCELED);
	}

	@Test
	@DisplayName("모두 배송 흐름의 다음 단계면 선택한 주문이 전부 바뀐다")
	void allNextStep_changed() {
		Orders first = saveOrder(OrderStatus.PAID);
		Orders second = saveOrder(OrderStatus.PAID);

		orderService.changeStatus(List.of(first.getId(), second.getId()), OrderStatus.PREPARING);

		assertThat(statusOf(first)).isEqualTo(OrderStatus.PREPARING);
		assertThat(statusOf(second)).isEqualTo(OrderStatus.PREPARING);
	}

	private Orders saveOrder(OrderStatus status) {
		return orderRepository.save(Orders.builder()
			.user(user)
			.orderNumber("ORDER-" + (++orderSeq))
			.orderName("상품1")
			.finalPrice(1000)
			.orderStatus(status)
			.build());
	}

	private OrderStatus statusOf(Orders order) {
		return orderRepository.findById(order.getId()).orElseThrow().getOrderStatus();
	}
}
