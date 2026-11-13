package com.commerce.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.commerce.common.enums.OrderStatus;
import com.commerce.order.domain.Orders;
import com.commerce.order.dto.OrderResponseDTO;
import com.commerce.user.domain.User;

/**
 * 사용자 주문 목록. 주문서에서 결제하기를 누르면 결제 준비(READY) 주문이 생기지만,
 * 결제창을 닫으면 사용자에게는 주문하지 않은 것이므로 목록에 보이지 않는다.
 */
class OrderListTest extends PaymentIntegrationTestSupport {

	@Test
	@DisplayName("결제 준비 주문은 목록과 개수에서 빠지고, 결제 실패·결제완료 주문은 보인다")
	void readyOrdersHidden() {
		// given
		Orders ready = prepareOrder();
		Orders paid = prepareOrder();
		Orders failed = prepareOrder();
		setStatus(paid, OrderStatus.PAID);
		setStatus(failed, OrderStatus.PAYMENT_FAILED);
		User user = userRepository.findById(userId).orElseThrow();

		// when
		Page<OrderResponseDTO> page = orderService.findOrderList(user, PageRequest.of(0, 10));

		// then
		assertThat(page.getContent()).extracting(OrderResponseDTO::getOrderNumber)
			.containsExactlyInAnyOrder(paid.getOrderNumber(), failed.getOrderNumber())
			.doesNotContain(ready.getOrderNumber());
		assertThat(page.getTotalElements()).isEqualTo(2);
	}

	private void setStatus(Orders order, OrderStatus status) {
		jdbcTemplate.update("update orders set order_status = ? where order_id = ?", status.name(), order.getId());
	}
}
