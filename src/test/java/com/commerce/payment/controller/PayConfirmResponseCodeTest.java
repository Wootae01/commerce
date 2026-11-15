package com.commerce.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.enums.OrderStatus;

class PayConfirmResponseCodeTest {

	@Test
	@DisplayName("결제 완료면 200")
	void paid_ok() {
		assertThat(PayConfirmResponseCode.from(OrderStatus.PAID)).isEqualTo(GeneralResponseCode.OK);
	}

	@Test
	@DisplayName("결과를 모르면 202 확인 중")
	void pending_confirming() {
		assertThat(PayConfirmResponseCode.from(OrderStatus.PAYMENT_PENDING))
			.isEqualTo(GeneralResponseCode.PAYMENT_CONFIRMING);
	}

	@Test
	@DisplayName("결제 실패면 400 결제 실패")
	void failed_paymentFailed() {
		assertThat(PayConfirmResponseCode.from(OrderStatus.PAYMENT_FAILED))
			.isEqualTo(GeneralResponseCode.PAYMENT_FAILED);
	}

	// 확정 요청이 끝나기 전에 보정 스케줄러가 승인하고, 그 뒤 취소·배송으로 바뀐 주문. 승인은 됐으므로 결제 실패가 아니다
	@ParameterizedTest
	@EnumSource(value = OrderStatus.class, names = {"PREPARING", "SHIPPING", "DELIVERED", "CANCEL_REQUESTED", "CANCELED"})
	@DisplayName("승인 뒤 상태가 바뀐 주문이면 200")
	void changedAfterApproval_ok(OrderStatus status) {
		assertThat(PayConfirmResponseCode.from(status)).isEqualTo(GeneralResponseCode.OK);
	}

	@Test
	@DisplayName("결제 확정 뒤 READY는 있을 수 없어 예외")
	void ready_throws() {
		assertThatThrownBy(() -> PayConfirmResponseCode.from(OrderStatus.READY))
			.isInstanceOf(IllegalStateException.class);
	}
}
