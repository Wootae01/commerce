package com.commerce.order.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.PaymentType;
import com.commerce.common.exception.ApiException;

class OrdersTest {

	@Nested
	@DisplayName("startPayment - 결제 확정 시작")
	class StartPayment {

		@Test
		@DisplayName("결제 준비 주문은 결제 진행 중이 된다")
		void readyToPaymentPending() {
			Orders order = orderWithStatus(OrderStatus.READY);

			order.startPayment();

			assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "READY")
		@DisplayName("결제 준비가 아닌 주문은 PAYMENT_ALREADY_PROCESSED 예외가 발생하고 상태가 그대로다")
		void notReadyRejected(OrderStatus status) {
			Orders order = orderWithStatus(status);

			assertThatThrownBy(order::startPayment)
				.isInstanceOf(ApiException.class)
				.extracting("responseCode").isEqualTo(GeneralResponseCode.PAYMENT_ALREADY_PROCESSED);
			assertThat(order.getOrderStatus()).isEqualTo(status);
		}
	}

	@Nested
	@DisplayName("markPaid - 결제 성공 반영")
	class MarkPaid {

		private final LocalDateTime approvedAt = LocalDateTime.of(2026, 10, 28, 12, 0);

		@Test
		@DisplayName("결제 진행 중 주문은 결제완료가 되고 결제 정보가 기록된다")
		void paymentPendingToPaid() {
			Orders order = orderWithStatus(OrderStatus.PAYMENT_PENDING);

			boolean applied = order.markPaid("pay-key-1", PaymentType.CARD, approvedAt);

			assertThat(applied).isTrue();
			assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAID);
			assertThat(order.getPaymentKey()).isEqualTo("pay-key-1");
			assertThat(order.getPaymentType()).isEqualTo(PaymentType.CARD);
			assertThat(order.getApprovedAt()).isEqualTo(approvedAt);
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PAYMENT_PENDING")
		@DisplayName("결제 진행 중이 아닌 주문은 반영하지 않고 false를 돌려준다")
		void notPendingIgnored(OrderStatus status) {
			Orders order = orderWithStatus(status);

			boolean applied = order.markPaid("pay-key-1", PaymentType.CARD, approvedAt);

			assertThat(applied).isFalse();
			assertThat(order.getOrderStatus()).isEqualTo(status);
			assertThat(order.getPaymentKey()).isNull();
			assertThat(order.getPaymentType()).isNull();
			assertThat(order.getApprovedAt()).isNull();
		}
	}

	@Nested
	@DisplayName("cancelPending - 결제 실패 반영")
	class CancelPending {

		@Test
		@DisplayName("결제 진행 중 주문은 취소완료가 된다")
		void paymentPendingToCanceled() {
			Orders order = orderWithStatus(OrderStatus.PAYMENT_PENDING);

			boolean applied = order.cancelPending();

			assertThat(applied).isTrue();
			assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "PAYMENT_PENDING")
		@DisplayName("결제 진행 중이 아닌 주문은 반영하지 않고 false를 돌려준다")
		void notPendingIgnored(OrderStatus status) {
			Orders order = orderWithStatus(status);

			boolean applied = order.cancelPending();

			assertThat(applied).isFalse();
			assertThat(order.getOrderStatus()).isEqualTo(status);
		}
	}

	@Nested
	@DisplayName("requestCancel - 취소 시작")
	class RequestCancel {

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, names = {"PAID", "READY"})
		@DisplayName("결제완료·결제 준비 주문은 취소요청이 된다")
		void toCancelRequested(OrderStatus status) {
			Orders order = orderWithStatus(status);

			order.requestCancel();

			assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PAID", "READY"})
		@DisplayName("그 외 주문은 ORDER_CANCEL_NOT_ALLOWED 예외가 발생하고 상태가 그대로다")
		void otherRejected(OrderStatus status) {
			Orders order = orderWithStatus(status);

			assertThatThrownBy(order::requestCancel)
				.isInstanceOf(ApiException.class)
				.extracting("responseCode").isEqualTo(GeneralResponseCode.ORDER_CANCEL_NOT_ALLOWED);
			assertThat(order.getOrderStatus()).isEqualTo(status);
		}
	}

	@Nested
	@DisplayName("completeCancel - 취소 완료")
	class CompleteCancel {

		@Test
		@DisplayName("취소요청 주문은 취소완료가 된다")
		void cancelRequestedToCanceled() {
			Orders order = orderWithStatus(OrderStatus.CANCEL_REQUESTED);

			order.completeCancel();

			assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "CANCEL_REQUESTED")
		@DisplayName("취소요청이 아닌 주문은 ORDER_CANCEL_NOT_ALLOWED 예외가 발생하고 상태가 그대로다")
		void notCancelRequestedRejected(OrderStatus status) {
			Orders order = orderWithStatus(status);

			assertThatThrownBy(order::completeCancel)
				.isInstanceOf(ApiException.class)
				.extracting("responseCode").isEqualTo(GeneralResponseCode.ORDER_CANCEL_NOT_ALLOWED);
			assertThat(order.getOrderStatus()).isEqualTo(status);
		}
	}

	@Nested
	@DisplayName("revertCancel - 토스 취소 실패 시 복원")
	class RevertCancel {

		@Test
		@DisplayName("취소요청 주문은 결제완료로 돌아간다")
		void cancelRequestedToPaid() {
			Orders order = orderWithStatus(OrderStatus.CANCEL_REQUESTED);

			boolean applied = order.revertCancel();

			assertThat(applied).isTrue();
			assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAID);
		}

		@ParameterizedTest
		@EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "CANCEL_REQUESTED")
		@DisplayName("취소요청이 아닌 주문은 그대로 두고 false를 돌려준다")
		void notCancelRequestedIgnored(OrderStatus status) {
			Orders order = orderWithStatus(status);

			boolean applied = order.revertCancel();

			assertThat(applied).isFalse();
			assertThat(order.getOrderStatus()).isEqualTo(status);
		}
	}

	@Nested
	@DisplayName("advanceDelivery - 관리자 배송 상태 변경")
	class AdvanceDelivery {

		@ParameterizedTest(name = "{0} → {1}")
		@CsvSource({
			"PAID, PREPARING",
			"PREPARING, SHIPPING",
			"SHIPPING, DELIVERED"
		})
		@DisplayName("배송 흐름의 다음 단계로 바뀐다")
		void nextStep(OrderStatus from, OrderStatus to) {
			Orders order = orderWithStatus(from);

			order.advanceDelivery(to);

			assertThat(order.getOrderStatus()).isEqualTo(to);
		}

		@ParameterizedTest(name = "{0} → {1}")
		@CsvSource({
			"CANCELED, PAID",
			"PAID, READY",
			"PAID, SHIPPING",
			"PAID, CANCELED",
			"READY, PREPARING",
			"SHIPPING, PREPARING",
			"DELIVERED, SHIPPING",
			"PREPARING, PREPARING"
		})
		@DisplayName("배송 흐름의 다음 단계가 아니면 ORDER_STATUS_CHANGE_NOT_ALLOWED 예외가 발생하고 상태가 그대로다")
		void otherRejected(OrderStatus from, OrderStatus to) {
			Orders order = orderWithStatus(from);

			assertThatThrownBy(() -> order.advanceDelivery(to))
				.isInstanceOf(ApiException.class)
				.extracting("responseCode").isEqualTo(GeneralResponseCode.ORDER_STATUS_CHANGE_NOT_ALLOWED);
			assertThat(order.getOrderStatus()).isEqualTo(from);
		}
	}

	private static Orders orderWithStatus(OrderStatus status) {
		return Orders.builder()
			.orderNumber("ORDER-1")
			.orderStatus(status)
			.build();
	}
}
