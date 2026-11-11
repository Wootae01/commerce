package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import com.commerce.common.enums.OrderStatus;
import com.commerce.order.domain.Orders;
import com.commerce.payment.dto.CancelResponseDTO;
import com.commerce.payment.service.PaymentTxService;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

/**
 * 주문 취소 정합성 테스트
 * 토스 취소 요청의 응답을 모르면 결제완료로 되돌리지 않고 취소 처리 중(CANCEL_REQUESTED)으로 두고,
 * 같은 멱등키(주문번호)로 다시 요청해 끝낸다. 토스에서 환불됐으면 주문도 CANCELED, 재고 복원은 1번이다.
 */
class CancelConsistencyTest extends PaymentIntegrationTestSupport {

	@Autowired PaymentTxService paymentTxService;

	@Test
	@DisplayName("취소 응답이 끊기면 같은 멱등키로 1번 다시 요청하고, 200이면 CANCELED·재고 복원 1번으로 끝난다")
	void connectionLost_retrySucceeds_canceled() {
		// given
		Orders order = paidOrder();
		stubFor(post(urlEqualTo(cancelUrlOf(order)))
			.inScenario("cancel-retry")
			.whenScenarioStateIs(Scenario.STARTED)
			.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
			.willSetStateTo("retried"));
		stubFor(post(urlEqualTo(cancelUrlOf(order)))
			.inScenario("cancel-retry")
			.whenScenarioStateIs("retried")
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("{ \"status\": \"CANCELED\", \"method\": \"카드\" }")));

		// when
		CancelResponseDTO response = payService.cancel(order.getOrderNumber(), userId);

		// then
		assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
		assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELED);
		assertThat(stock()).as("재고 복원 1번").isEqualTo(INITIAL_STOCK);
		verify(2, postRequestedFor(urlEqualTo(cancelUrlOf(order)))
			.withHeader("Idempotency-Key", equalTo(order.getOrderNumber())));
	}

	@Test
	@DisplayName("다시 요청해도 응답이 끊기면 결제완료로 되돌리지 않고 취소 처리 중으로 두며, 재고는 그대로다")
	void connectionLostTwice_cancelRequested() {
		// given - 토스에서는 이미 환불됐을 수 있다
		Orders order = paidOrder();
		stubFor(post(urlEqualTo(cancelUrlOf(order)))
			.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		// when
		CancelResponseDTO response = payService.cancel(order.getOrderNumber(), userId);

		// then - 예외 없이 처리 중을 돌려준다. 보정 스케줄러가 같은 키로 다시 요청해 끝낸다
		assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
		assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCEL_REQUESTED);
		assertThat(stock()).as("재고").isEqualTo(INITIAL_STOCK - 1);
		verify(2, postRequestedFor(urlEqualTo(cancelUrlOf(order))));
	}

	@ParameterizedTest(name = "{0} {1} → 요청 {2}번")
	@CsvSource({
		// 토스와 통신하지 못함: 같은 키로 다시 요청한다
		"500, FAILED_INTERNAL_SYSTEM_PROCESSING, 2",
		// 401은 멱등키에 저장되지 않고(#10), 429·409는 토스 쪽 상황이다. 다시 요청하지 않고 스케줄러에 맡긴다
		"401, UNAUTHORIZED_KEY, 1",
		"429, TOO_MANY_REQUESTS, 1",
		"409, IDEMPOTENT_REQUEST_PROCESSING, 1"
	})
	@DisplayName("결과를 알 수 없는 오류 응답이면 취소 처리 중으로 두고 재고는 그대로다")
	void unknownErrorResponse_cancelRequested(int status, String code, int expectedRequests) {
		// given
		Orders order = paidOrder();
		stubCancel(order, status, "{ \"code\": \"" + code + "\", \"message\": \"오류\" }");

		// when
		CancelResponseDTO response = payService.cancel(order.getOrderNumber(), userId);

		// then
		assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
		assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCEL_REQUESTED);
		assertThat(stock()).as("재고").isEqualTo(INITIAL_STOCK - 1);
		verify(expectedRequests, postRequestedFor(urlEqualTo(cancelUrlOf(order))));
	}

	@Test
	@DisplayName("사용자 요청과 보정 스케줄러가 같은 주문의 취소 완료를 동시에 반영해도 재고는 1번만 복원되고, 둘 다 CANCELED를 돌려받는다")
	void applyCancelSuccess_concurrent_restoredOnce() throws Exception {
		// given - 취소 처리 중인 주문
		Orders order = paidOrder();
		paymentTxService.beginCancel(order.getId());

		// 먼저 들어온 반영이 재고를 복원하는 중(주문 락을 잡은 채)에 멈추게 한다
		CountDownLatch firstPaused = new CountDownLatch(1);
		CountDownLatch resumeFirst = new CountDownLatch(1);
		AtomicInteger restoreCalls = new AtomicInteger();
		doAnswer(invocation -> {
			if (restoreCalls.incrementAndGet() == 1) {
				firstPaused.countDown();
				resumeFirst.await(5, TimeUnit.SECONDS);
			}
			return invocation.callRealMethod();
		}).when(productJdbcRepository).updateOptionStock(anyMap(), eq(true));

		// when
		ExecutorService pool = Executors.newFixedThreadPool(2);
		Future<OrderStatus> first = pool.submit(() -> paymentTxService.applyCancelSuccess(order.getId(), true));
		assertThat(firstPaused.await(5, TimeUnit.SECONDS)).isTrue();

		Future<OrderStatus> second = pool.submit(() -> paymentTxService.applyCancelSuccess(order.getId(), true));
		// - 락이 없으면: 두 번째 반영도 취소 처리 중을 보고 재고를 한 번 더 복원한다
		// - 락이 있으면: 첫 반영이 끝날 때까지 기다렸다가 CANCELED를 보고 아무것도 하지 않는다
		Thread.sleep(1000);
		resumeFirst.countDown();

		OrderStatus firstResult = first.get(10, TimeUnit.SECONDS);
		OrderStatus secondResult = second.get(10, TimeUnit.SECONDS);
		pool.shutdown();

		// then
		assertThat(firstResult).isEqualTo(OrderStatus.CANCELED);
		assertThat(secondResult).as("나중 반영도 실패하지 않는다").isEqualTo(OrderStatus.CANCELED);
		assertThat(stock()).as("재고 복원 1번").isEqualTo(INITIAL_STOCK);
		Mockito.verify(productJdbcRepository, times(1)).updateOptionStock(anyMap(), eq(true));
	}

	@Test
	@DisplayName("200 응답을 해석하지 못하면 다시 요청하지 않고 취소 처리 중으로 둔다")
	void unreadableResponse_cancelRequested() {
		// given - 같은 키로 다시 요청해도 같은 응답이 온다
		Orders order = paidOrder();
		stubCancel(order, 200, "{ not json");

		// when
		CancelResponseDTO response = payService.cancel(order.getOrderNumber(), userId);

		// then
		assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.CANCEL_REQUESTED);
		assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCEL_REQUESTED);
		assertThat(stock()).as("재고").isEqualTo(INITIAL_STOCK - 1);
		verify(1, postRequestedFor(urlEqualTo(cancelUrlOf(order))));
	}
}
