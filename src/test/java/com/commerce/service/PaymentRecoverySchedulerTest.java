package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.commerce.common.enums.OrderStatus;
import com.commerce.order.domain.Orders;
import com.commerce.payment.dto.PayConfirmDTO;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;

/**
 * 보정 스케줄러: PAYMENT_PENDING이 된 지 1분 지난 주문을 같은 멱등키로 다시 승인 요청해 확정한다.
 * 스케줄러 실행 메서드를 직접 호출하고, 시간 조건은 updated_at을 과거로 바꿔 만든다.
 */
@ExtendWith(OutputCaptureExtension.class)
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

	@Test
	@DisplayName("토스 연결 실패(네트워크 오류)가 3번 연속이면 그 실행을 멈추고, 다음 실행에서 시도하지 못한 주문부터 이어 처리한다")
	void threeConsecutiveConnectionFailures_stopRun_resumeNext() {
		// given - 오래된 순 o1..o5
		List<Orders> orders = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			Orders order = pendingOrder();
			markUpdatedAt(order, LocalDateTime.now().minusMinutes(10 - i));
			orders.add(order);
		}
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		// when 1
		paymentRecoveryScheduler.recoverPendingPayments();

		// then 1 - o1~o3에서 3번 연속 실패 → 중단
		verify(3, postRequestedFor(urlEqualTo("/v1/payments/confirm")));
		assertThat(requestsFor(orders.get(3))).isZero();
		assertThat(requestsFor(orders.get(4))).isZero();

		// when 2
		paymentRecoveryScheduler.recoverPendingPayments();

		// then 2 - 시도하지 못한 o4, o5부터 요청한다
		assertThat(requestsFor(orders.get(3))).isEqualTo(1);
		assertThat(requestsFor(orders.get(4))).isEqualTo(1);
		orders.forEach(order -> assertThat(statusOf(order)).isEqualTo(OrderStatus.PAYMENT_PENDING));
	}

	@Test
	@DisplayName("연결 실패 사이에 토스가 응답한 주문이 있으면 연속 횟수를 다시 세므로, 멈추지 않고 모두 요청한다")
	void connectionFailuresInterrupted_noStop() {
		// given - 실패, 실패, 409(토스가 응답), 실패, 실패
		List<Orders> orders = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			Orders order = pendingOrder();
			markUpdatedAt(order, LocalDateTime.now().minusMinutes(10 - i));
			orders.add(order);
		}
		for (int i : List.of(0, 1, 3, 4)) {
			stubConfirmFor(orders.get(i), aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));
		}
		stubConfirmFor(orders.get(2), aResponse()
			.withStatus(409)
			.withHeader("Content-Type", "application/json")
			.withBody("{ \"code\": \"IDEMPOTENT_REQUEST_PROCESSING\", \"message\": \"이전 멱등 요청이 처리중이에요.\" }"));

		// when
		paymentRecoveryScheduler.recoverPendingPayments();

		// then
		verify(5, postRequestedFor(urlEqualTo("/v1/payments/confirm")));
	}

	@Test
	@DisplayName("결과를 모르는 주문이 100건 넘게 쌓여 있어도, 새로 PENDING이 된 주문은 다음 실행에서 요청한다 (시도한 주문은 뒤로 밀린다)")
	void manyUnknownOrders_newPendingOrderNotStarved() {
		// given - 결과를 계속 모르는 주문 100건(10분 전) + 새 PENDING 주문(2분 전)
		List<Orders> stuck = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			stuck.add(prepareOrder());
		}
		jdbcTemplate.update("""
			update orders set order_status = 'PAYMENT_PENDING', payment_key = concat('pk-', order_number), updated_at = ?
			where order_status = 'READY'
			""", LocalDateTime.now().minusMinutes(10));
		Orders newOrder = pendingOrder();
		markUpdatedAt(newOrder, LocalDateTime.now().minusMinutes(2));
		stubConfirm(409, """
			{ "code": "IDEMPOTENT_REQUEST_PROCESSING", "message": "이전 멱등 요청이 처리중이에요." }
			""");
		LocalDateTime stuckPendingSince = updatedAtOf(stuck.get(0));

		// when 1 - 오래된 100건이 한 번에 처리할 수 있는 수를 채운다
		paymentRecoveryScheduler.recoverPendingPayments();

		// then 1
		verify(100, postRequestedFor(urlEqualTo("/v1/payments/confirm")));
		assertThat(requestsFor(newOrder)).isZero();

		// when 2
		paymentRecoveryScheduler.recoverPendingPayments();

		// then 2 - 아직 시도하지 않은 새 주문이 먼저. 시도해도 PENDING이 된 시각(updated_at)은 그대로다
		assertThat(requestsFor(newOrder)).isEqualTo(1);
		assertThat(updatedAtOf(stuck.get(0))).isEqualTo(stuckPendingSince);
	}

	@Test
	@DisplayName("결과를 30분 넘게 모르는 주문은 에러 로그를 남기되, 30분을 넘긴 뒤 첫 시도에서 한 번만 남긴다")
	void unknownOver30Minutes_errorLoggedOnce(CapturedOutput output) {
		// given
		Orders longPending = pendingOrder();
		Orders shortPending = pendingOrder();
		markUpdatedAt(longPending, LocalDateTime.now().minusMinutes(31));
		markUpdatedAt(shortPending, LocalDateTime.now().minusMinutes(10));
		stubConfirm(409, """
			{ "code": "IDEMPOTENT_REQUEST_PROCESSING", "message": "이전 멱등 요청이 처리중이에요." }
			""");

		// when
		paymentRecoveryScheduler.recoverPendingPayments();
		paymentRecoveryScheduler.recoverPendingPayments();

		// then
		assertThat(logLines(output, "30분 넘게", longPending)).isEqualTo(1);
		assertThat(logLines(output, "30분 넘게", shortPending)).isZero();
		verify(4, postRequestedFor(urlEqualTo("/v1/payments/confirm")));
	}

	@Test
	@DisplayName("결과를 24시간 넘게 모르는 주문은 자동 처리에서 빼고(승인 요청 안 함), 에러 로그를 한 번만 남긴다")
	void unknownOver24Hours_excludedAndErrorLoggedOnce(CapturedOutput output) {
		// given
		Orders expired = pendingOrder();
		markUpdatedAt(expired, LocalDateTime.now().minusHours(25));
		stubConfirm(200, """
			{ "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
			""");

		// when
		paymentRecoveryScheduler.recoverPendingPayments();
		paymentRecoveryScheduler.recoverPendingPayments();

		// then - 사람이 확인할 때까지 PENDING으로 두고 재고도 그대로
		assertThat(requestsFor(expired)).isZero();
		assertThat(statusOf(expired)).isEqualTo(OrderStatus.PAYMENT_PENDING);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
		assertThat(logLines(output, "24시간 넘게", expired)).isEqualTo(1);
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

	// 이 주문(멱등키 = 주문번호)의 승인 요청에만 응답을 정한다
	private void stubConfirmFor(Orders order, ResponseDefinitionBuilder response) {
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.withHeader("Idempotency-Key", equalTo(order.getOrderNumber()))
			.willReturn(response));
	}

	private int requestsFor(Orders order) {
		return findAll(postRequestedFor(urlEqualTo("/v1/payments/confirm"))
			.withHeader("Idempotency-Key", equalTo(order.getOrderNumber()))).size();
	}

	private LocalDateTime updatedAtOf(Orders order) {
		return jdbcTemplate.queryForObject("select updated_at from orders where order_number = ?",
			LocalDateTime.class, order.getOrderNumber());
	}

	// 이 주문번호가 들어간 로그 중 keyword가 있는 줄 수
	private long logLines(CapturedOutput output, String keyword, Orders order) {
		return output.getOut().lines()
			.filter(line -> line.contains(keyword) && line.contains(order.getOrderNumber()))
			.count();
	}

	private void stubConfirm(int status, String body) {
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBody(body)));
	}
}
