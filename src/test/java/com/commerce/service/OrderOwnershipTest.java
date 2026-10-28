package com.commerce.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.RoleType;
import com.commerce.common.exception.ApiException;
import com.commerce.order.domain.Orders;
import com.commerce.payment.dto.PayConfirmDTO;
import com.commerce.user.domain.User;

/**
 * 남의 주문번호로 요청하면 없는 주문번호와 똑같이 ORDER_NOT_FOUND로 응답한다.
 * 응답만 보고는 그 주문번호가 있는지 알 수 없어야 하고, 서버 로그로는 계속 구별할 수 있어야 한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class OrderOwnershipTest extends PaymentIntegrationTestSupport {

	private static final String MISSING_ORDER_NUMBER = "0".repeat(32);

	private Long otherUserId;

	@BeforeEach
	void setUpOtherUser() {
		otherUserId = userRepository.save(User.builder()
			.customerPaymentKey("otherCustomerPaymentKey")
			.email("other@naver.com")
			.name("김철수")
			.phone("01098765432")
			.role(RoleType.ROLE_USER)
			.username("other")
			.build()).getId();
	}

	@Test
	@DisplayName("주문 조회: 남의 주문번호면 없는 주문번호와 같은 ORDER_NOT_FOUND가 나오고, 경고 로그는 남는다")
	void findMyOrder_othersOrder_sameAsMissing(CapturedOutput output) {
		// given
		Orders order = prepareOrder();

		// when & then
		assertThatThrownBy(() -> orderService.findMyOrder(MISSING_ORDER_NUMBER, otherUserId))
			.isInstanceOf(ApiException.class)
			.extracting("responseCode").isEqualTo(GeneralResponseCode.ORDER_NOT_FOUND);
		assertThatThrownBy(() -> orderService.findMyOrder(order.getOrderNumber(), otherUserId))
			.isInstanceOf(ApiException.class)
			.extracting("responseCode").isEqualTo(GeneralResponseCode.ORDER_NOT_FOUND);
		assertThat(output.getOut()).contains("본인 주문이 아닙니다. orderNumber=" + order.getOrderNumber()
			+ ", orderUserId=" + userId + ", userId=" + otherUserId);
	}

	@Test
	@DisplayName("주문 조회: 본인 주문은 그대로 조회된다")
	void findMyOrder_ownOrder_found() {
		// given
		Orders order = prepareOrder();

		// when
		Orders found = orderService.findMyOrder(order.getOrderNumber(), userId);

		// then
		assertThat(found.getId()).isEqualTo(order.getId());
	}

	@Test
	@DisplayName("결제 승인: 남의 주문번호면 ORDER_NOT_FOUND가 나오고, 토스를 부르지 않고 재고·주문 상태도 그대로다")
	void confirm_othersOrder_notFound() {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO("paymentKey", order.getOrderNumber(), order.getFinalPrice());

		// when & then
		assertThatThrownBy(() -> payService.confirm(req, otherUserId))
			.isInstanceOf(ApiException.class)
			.extracting("responseCode").isEqualTo(GeneralResponseCode.ORDER_NOT_FOUND);
		verify(tossPaymentClient, never()).confirm(any());
		assertThat(statusOf(order)).isEqualTo(OrderStatus.READY);
		assertThat(stock()).isEqualTo(INITIAL_STOCK);
	}

	@Test
	@DisplayName("주문 취소: 남의 주문번호면 ORDER_NOT_FOUND가 나오고, 주문 상태는 그대로다")
	void cancel_othersOrder_notFound() {
		// given
		Orders order = prepareOrder();

		// when & then
		assertThatThrownBy(() -> payService.cancel(order.getOrderNumber(), "단순 변심", otherUserId))
			.isInstanceOf(ApiException.class)
			.extracting("responseCode").isEqualTo(GeneralResponseCode.ORDER_NOT_FOUND);
		assertThat(statusOf(order)).isEqualTo(OrderStatus.READY);
	}
}
