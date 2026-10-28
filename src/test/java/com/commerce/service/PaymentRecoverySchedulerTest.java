package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.commerce.common.enums.OrderStatus;
import com.commerce.order.domain.Orders;
import com.commerce.payment.dto.PayConfirmDTO;

/**
 * 보정 스케줄러: PAYMENT_PENDING이 된 지 1분 지난 주문을 같은 멱등키로 다시 승인 요청해 확정한다.
 * 스케줄러 실행 메서드를 직접 호출하고, 시간 조건은 updated_at을 과거로 바꿔 만든다.
 */
class PaymentRecoverySchedulerTest extends PaymentIntegrationTestSupport {

	@Test
	@DisplayName("PENDING이 된 지 1분 지난 주문은 같은 멱등키·paymentKey·금액으로 1번 승인을 다시 요청하고, 200이면 PAID로 확정한다")
	void pendingOverOneMinute_approved_paid() {
		// given
		Orders order = pendingOrder();
		PayConfirmDTO req = confirmRequestOf(order);
		markUpdatedAt(order, LocalDateTime.now().minusMinutes(2));
		stubConfirm(200, """
			{ "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
			""");

		// when
		paymentRecoveryScheduler.recoverPendingPayments();

		// then - 재고는 PENDING이 될 때 이미 차감됐으므로 그대로 둔다
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.PAID);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
		verify(1, postRequestedFor(urlEqualTo("/v1/payments/confirm"))
			.withHeader("Idempotency-Key", equalTo(order.getOrderNumber()))
			.withRequestBody(matchingJsonPath("$.paymentKey", equalTo(req.getPaymentKey())))
			.withRequestBody(matchingJsonPath("$.orderId", equalTo(order.getOrderNumber())))
			.withRequestBody(matchingJsonPath("$.amount", equalTo(String.valueOf(order.getFinalPrice())))));
	}

	@Test
	@DisplayName("다시 요청한 승인이 4xx(거절)면 재고를 복원하고 CANCELED로 확정한다")
	void pendingOverOneMinute_rejected_canceled() {
		// given
		Orders order = pendingOrder();
		markUpdatedAt(order, LocalDateTime.now().minusMinutes(2));
		stubConfirm(400, """
			{ "code": "REJECT_CARD_PAYMENT", "message": "한도초과 혹은 잔액부족으로 결제에 실패했습니다." }
			""");

		// when
		paymentRecoveryScheduler.recoverPendingPayments();

		// then
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
		assertThat(stock()).isEqualTo(INITIAL_STOCK);
	}

	@Test
	@DisplayName("다시 요청한 승인이 5xx면 그 실행에서는 바로 재요청하지 않고 결제 조회로 확인한다")
	void pendingOverOneMinute_serverError_noImmediateRetry() {
		// given
		Orders order = pendingOrder();
		PayConfirmDTO req = confirmRequestOf(order);
		markUpdatedAt(order, LocalDateTime.now().minusMinutes(2));
		stubConfirm(500, """
			{ "code": "FAILED_INTERNAL_SYSTEM_PROCESSING", "message": "내부 시스템 처리 작업이 실패했습니다." }
			""");
		stubFor(get(urlEqualTo("/v1/payments/" + req.getPaymentKey()))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{ \"status\": \"IN_PROGRESS\" }")));

		// when
		paymentRecoveryScheduler.recoverPendingPayments();

		// then - 다음 실행에서 다시 요청한다
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
		verify(1, postRequestedFor(urlEqualTo("/v1/payments/confirm")));
		verify(1, getRequestedFor(urlEqualTo("/v1/payments/" + req.getPaymentKey())));
	}

	@Test
	@DisplayName("PENDING이 된 지 1분이 안 된 주문과 PENDING이 아닌 주문은 건드리지 않는다")
	void notTargets_untouched() {
		// given
		Orders recentPending = pendingOrder(); // 방금 PENDING이 됨
		Orders ready = prepareOrder();
		Orders paid = prepareOrder();
		stubConfirm(200, """
			{ "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
			""");
		payService.confirm(confirmRequestOf(paid), userId);
		Orders canceled = prepareOrder();
		stubConfirm(400, """
			{ "code": "REJECT_CARD_PAYMENT", "message": "에러" }
			""");
		payService.confirm(confirmRequestOf(canceled), userId);

		// PENDING이 아닌 주문은 오래됐어도 대상이 아니다
		LocalDateTime twoMinutesAgo = LocalDateTime.now().minusMinutes(2);
		markUpdatedAt(ready, twoMinutesAgo);
		markUpdatedAt(paid, twoMinutesAgo);
		markUpdatedAt(canceled, twoMinutesAgo);
		resetAllRequests();

		// when
		paymentRecoveryScheduler.recoverPendingPayments();

		// then
		verify(0, postRequestedFor(urlEqualTo("/v1/payments/confirm")));
		assertThat(statusOf(recentPending)).isEqualTo(OrderStatus.PAYMENT_PENDING);
		assertThat(statusOf(ready)).isEqualTo(OrderStatus.READY);
		assertThat(statusOf(paid)).isEqualTo(OrderStatus.PAID);
		assertThat(statusOf(canceled)).isEqualTo(OrderStatus.CANCELED);
	}

	@Test
	@DisplayName("한 주문의 처리에서 예외가 나도 나머지 주문은 계속 확정한다")
	void oneOrderFails_othersStillProcessed() {
		// given - 오래된 순으로 처리하므로 broken을 먼저 처리한다
		Orders broken = pendingOrder();
		Orders normal = pendingOrder();
		markUpdatedAt(broken, LocalDateTime.now().minusMinutes(3));
		markUpdatedAt(normal, LocalDateTime.now().minusMinutes(2));
		stubConfirm(200, """
			{ "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
			""");
		// broken은 승인 응답이 비어 있어 결과를 반영하다 예외가 난다
		doAnswer(invocation -> {
			PayConfirmDTO req = invocation.getArgument(0);
			if (req.getOrderId().equals(broken.getOrderNumber())) {
				return null;
			}
			return invocation.callRealMethod();
		}).when(tossPaymentClient).confirm(any());

		// when
		paymentRecoveryScheduler.recoverPendingPayments();

		// then
		assertThat(statusOf(broken)).isEqualTo(OrderStatus.PAYMENT_PENDING);
		assertThat(statusOf(normal)).isEqualTo(OrderStatus.PAID);
	}

	// 토스가 처리 중(409)이라고 해서 PENDING으로 남은 주문
	private Orders pendingOrder() {
		Orders order = prepareOrder();
		stubConfirm(409, """
			{ "code": "IDEMPOTENT_REQUEST_PROCESSING", "message": "이전 멱등 요청이 처리중이에요." }
			""");
		OrderStatus result = payService.confirm(confirmRequestOf(order), userId);
		assertThat(result).isEqualTo(OrderStatus.PAYMENT_PENDING);
		resetAllRequests();
		return orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
	}

	// 주문마다 paymentKey를 주문번호에서 만들어, 확정 요청과 스케줄러 재요청의 값을 비교할 수 있게 한다
	private PayConfirmDTO confirmRequestOf(Orders order) {
		String paymentKey = UUID.nameUUIDFromBytes(order.getOrderNumber().getBytes()).toString();
		return new PayConfirmDTO(paymentKey, order.getOrderNumber(), order.getFinalPrice());
	}

	private void stubConfirm(int status, String body) {
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBody(body)));
	}
}
