package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import com.commerce.common.code.ExternalResponseCode;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.exception.ApiException;
import com.commerce.order.domain.Orders;
import com.commerce.payment.dto.PayConfirmDTO;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import lombok.extern.slf4j.Slf4j;

/**
 * 결제 확정 정합성 테스트
 * 1) 같은 주문에 확정 요청이 동시에 2건 들어오는 경우
 * 2) 토스 승인 요청 후 응답이 끊기는 경우
 * 3) 결과를 모르는 주문을 보정 스케줄러가 확정하는 경우
 */
@Slf4j
class PayConsistencyTest extends PaymentIntegrationTestSupport {

	// 기본 승인 응답: 200
	@BeforeEach
	void stubConfirmApproved() {
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(200)
				.withFixedDelay(600)
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{ "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
					""")));
	}

	@Test
	@DisplayName("같은 주문에 확정 요청 2건이 겹치면 1건만 성공하고, 재고 차감과 토스 승인도 1번만 일어난다")
	void duplicateConfirm_processedOnce() throws Exception {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());

		// 실제 토스처럼: 첫 승인만 성공, 같은 결제의 두 번째 승인은 거절
		// (WireMock 시나리오는 두 요청이 동시에 오면 둘 다 첫 요청으로 판단할 수 있어, 호출 순서를 여기서 정한다)
		AtomicInteger confirmCalls = new AtomicInteger();
		doAnswer(invocation -> {
			if (confirmCalls.incrementAndGet() == 1) {
				return invocation.callRealMethod(); // 첫 승인: WireMock 호출 → 200
			}
			// 실제 토스는 같은 결제의 두 번째 승인을 400 ALREADY_PROCESSED_PAYMENT로 거절한다.
			// TossPaymentClient는 400 응답을 이 예외로 바꿔 던진다.
			throw new ApiException(ExternalResponseCode.PG_APPROVAL_ERROR);
		}).when(tossPaymentClient).confirm(any());

		// 두 요청이 모두 검증(READY 확인)을 통과한 뒤, 재고 차감 직전에서 서로를 기다리게 한다.
		// - 락이 있으면: 두 번째 요청은 주문 행 락에서 막혀 여기까지 오지 못한다 → 첫 요청이 2초 후 진행
		// - 락이 없으면: 두 요청 모두 READY를 보고 여기 도착 → 동시에 재고 차감
		CountDownLatch arrived = new CountDownLatch(2);
		doAnswer(invocation -> {
			arrived.countDown();
			arrived.await(2, TimeUnit.SECONDS);
			return invocation.callRealMethod();
		}).when(productJdbcRepository).updateOptionStock(anyMap(), eq(false));

		// when
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(2);
		AtomicInteger success = new AtomicInteger();
		List<Throwable> failures = new ArrayList<>();

		for (int i = 0; i < 2; i++) {
			pool.submit(() -> {
				try {
					start.await();
					payService.confirm(req, userId);
					success.incrementAndGet();
				} catch (Throwable e) {
					synchronized (failures) {
						failures.add(e);
					}
				} finally {
					done.countDown();
				}
			});
		}
		start.countDown();
		assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
		pool.shutdown();

		// then
		failures.forEach(e -> log.info("거절된 요청: {}", e.toString()));
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();

		assertThat(after.getOrderStatus()).as("주문 상태").isEqualTo(OrderStatus.PAID);
		assertThat(stock()).as("재고").isEqualTo(INITIAL_STOCK - 1);
		assertThat(success.get()).as("성공한 요청 수").isEqualTo(1);
		assertThat(failures).hasSize(1);
		Mockito.verify(tossPaymentClient, times(1)).confirm(any()); // 토스 승인 요청 1번

		// 재고 차감(updateOptionStock(..., false))은 1번만 실행돼야 한다
		Mockito.verify(productJdbcRepository, times(1)).updateOptionStock(anyMap(), eq(false));
	}

	@Test
	@DisplayName("재요청까지 응답이 끊기면 토스 결제 조회(DONE)로 PAID 처리하고, 예외 없이 PAID를 돌려준다")
	void responseLost_tossApproved_paid() {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());
		stubConfirmConnectionReset();
		stubGetPayment(req.getPaymentKey(), """
			{ "status": "DONE", "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
			""");

		// when
		OrderStatus result = payService.confirm(req, userId);

		// then - 토스에서는 결제 완료 → 우리 주문도 PAID, 재고는 차감된 상태 유지
		assertThat(result).isEqualTo(OrderStatus.PAID);
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.PAID);
		assertThat(after.getPaymentKey()).isEqualTo(req.getPaymentKey());
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
		verify(2, postRequestedFor(urlEqualTo("/v1/payments/confirm")));
		verify(1, getRequestedFor(urlEqualTo("/v1/payments/" + req.getPaymentKey())));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"ABORTED", "EXPIRED", "CANCELED"})
	@DisplayName("재요청까지 응답이 끊겼고 토스가 실패·만료·취소라고 하면, 재고를 복원하고 CANCELED를 돌려준다")
	void responseLost_tossFailed_canceled(String tossStatus) {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());
		stubConfirmConnectionReset();
		stubGetPayment(req.getPaymentKey(), "{ \"status\": \"" + tossStatus + "\" }");

		// when
		OrderStatus result = payService.confirm(req, userId);

		// then
		assertThat(result).isEqualTo(OrderStatus.CANCELED);
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
		assertThat(stock()).isEqualTo(INITIAL_STOCK);
		verify(2, postRequestedFor(urlEqualTo("/v1/payments/confirm")));
	}

	@Test
	@DisplayName("승인 요청에 주문번호를 멱등키(Idempotency-Key)로 붙인다")
	void confirm_sendsOrderNumberAsIdempotencyKey() {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());

		// when
		payService.confirm(req, userId);

		// then
		verify(1, postRequestedFor(urlEqualTo("/v1/payments/confirm"))
			.withHeader("Idempotency-Key", equalTo(order.getOrderNumber())));
	}

	@Test
	@DisplayName("승인 응답이 끊기면 같은 멱등키로 1번 다시 요청하고, 200이면 예외 없이 PAID를 돌려준다")
	void responseLost_retrySucceeds_paid() {
		// given - 첫 승인 요청은 응답 도중 끊기고, 재요청은 200
		Orders order = prepareOrder();
		stubConfirmFirstThen(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());

		// when
		OrderStatus result = payService.confirm(req, userId);

		// then
		assertThat(result).isEqualTo(OrderStatus.PAID);
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.PAID);
		assertThat(after.getPaymentKey()).isEqualTo(req.getPaymentKey());
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
		verify(2, postRequestedFor(urlEqualTo("/v1/payments/confirm"))
			.withHeader("Idempotency-Key", equalTo(order.getOrderNumber())));
		verify(0, getRequestedFor(urlEqualTo("/v1/payments/" + req.getPaymentKey())));
	}

	@Test
	@DisplayName("승인 요청이 5xx면 같은 멱등키로 1번 다시 요청하고, 200이면 PAID를 돌려준다")
	void serverError_retrySucceeds_paid() {
		// given - 첫 승인 요청은 500, 재요청은 200
		Orders order = prepareOrder();
		stubConfirmFirstThen(aResponse()
			.withStatus(500)
			.withHeader("Content-Type", "application/json")
			.withBody("""
				{ "code": "FAILED_INTERNAL_SYSTEM_PROCESSING", "message": "내부 시스템 처리 작업이 실패했습니다." }
				"""));
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());

		// when
		OrderStatus result = payService.confirm(req, userId);

		// then
		assertThat(result).isEqualTo(OrderStatus.PAID);
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.PAID);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
		verify(2, postRequestedFor(urlEqualTo("/v1/payments/confirm"))
			.withHeader("Idempotency-Key", equalTo(order.getOrderNumber())));
	}

	@ParameterizedTest(name = "{0} {1}")
	@CsvSource({
		"400, REJECT_CARD_PAYMENT",
		"403, FORBIDDEN_REQUEST" // 금액 불일치. 토스 테스트 키로 확인한 응답(#10)
	})
	@DisplayName("승인 요청이 4xx(거절)면 다시 요청하지 않고, 재고를 복원하고 CANCELED를 돌려준다")
	void clientError_noRetry_canceled(int status, String code) {
		// given
		Orders order = prepareOrder();
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBody("{ \"code\": \"" + code + "\", \"message\": \"에러\" }")));
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());

		// when
		OrderStatus result = payService.confirm(req, userId);

		// then
		assertThat(result).isEqualTo(OrderStatus.CANCELED);
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
		assertThat(stock()).isEqualTo(INITIAL_STOCK);
		verify(1, postRequestedFor(urlEqualTo("/v1/payments/confirm")));
		verify(0, getRequestedFor(urlEqualTo("/v1/payments/" + req.getPaymentKey())));
	}

	@ParameterizedTest(name = "조회 {0} → {1}, 재고 {2}")
	@CsvSource({
		"DONE, PAID, 9",
		"ABORTED, CANCELED, 10",
		"IN_PROGRESS, PAYMENT_PENDING, 9"
	})
	@DisplayName("재요청까지 5xx면 paymentKey로 결제를 조회해, 승인이면 PAID, 실패면 재고 복원 후 CANCELED, 처리 중이면 PENDING을 돌려준다")
	void serverErrorTwice_queryDecides(String tossStatus, OrderStatus expected, int expectedStock) {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(500)
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{ "code": "FAILED_INTERNAL_SYSTEM_PROCESSING", "message": "내부 시스템 처리 작업이 실패했습니다." }
					""")));
		stubGetPayment(req.getPaymentKey(),
			"{ \"status\": \"" + tossStatus + "\", \"method\": \"카드\", \"approvedAt\": \"2026-01-03T01:23:45+09:00\" }");

		// when
		OrderStatus result = payService.confirm(req, userId);

		// then
		assertThat(result).isEqualTo(expected);
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(expected);
		assertThat(stock()).isEqualTo(expectedStock);
		verify(2, postRequestedFor(urlEqualTo("/v1/payments/confirm")));
		verify(1, getRequestedFor(urlEqualTo("/v1/payments/" + req.getPaymentKey())));
	}

	@ParameterizedTest(name = "{0} {1}")
	@CsvSource({
		"401, UNAUTHORIZED_KEY",
		"429, PROVIDER_ERROR"
	})
	@DisplayName("승인 요청이 401·429면 결과를 모르는 것으로 보고 결제를 조회해, 승인(DONE)돼 있으면 PAID를 돌려준다")
	void unauthorizedOrTooManyRequests_queryDone_paid(int status, String code) {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBody("{ \"code\": \"" + code + "\", \"message\": \"에러\" }")));
		stubGetPayment(req.getPaymentKey(), """
			{ "status": "DONE", "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
			""");

		// when
		OrderStatus result = payService.confirm(req, userId);

		// then
		assertThat(result).isEqualTo(OrderStatus.PAID);
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.PAID);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
		verify(1, postRequestedFor(urlEqualTo("/v1/payments/confirm"))); // 바로 재요청하지 않고 조회로 확인
		verify(1, getRequestedFor(urlEqualTo("/v1/payments/" + req.getPaymentKey())));
	}

	@Test
	@DisplayName("토스가 같은 멱등키의 요청을 아직 처리 중(409)이라고 하면, 취소하지 않고 PENDING을 돌려준다")
	void idempotentRequestProcessing_pending() {
		// given
		Orders order = prepareOrder();
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(409)
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{ "code": "IDEMPOTENT_REQUEST_PROCESSING", "message": "이전 멱등 요청이 처리중이에요." }
					""")));
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());

		// when
		OrderStatus result = payService.confirm(req, userId);

		// then - 결과를 모르므로 재고는 차감된 채로 둔다. 토스가 처리 중이라고 알려 줬으니 재요청·조회하지 않는다
		assertThat(result).isEqualTo(OrderStatus.PAYMENT_PENDING);
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
		verify(1, postRequestedFor(urlEqualTo("/v1/payments/confirm")));
		verify(0, getRequestedFor(urlEqualTo("/v1/payments/" + req.getPaymentKey())));
	}

	@Test
	@DisplayName("이미 PAID인 주문에 같은 paymentKey로 확정을 다시 요청하면, 토스를 부르지 않고 PAID를 돌려준다")
	void reconfirmPaidOrder_paidWithoutTossCall() {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());
		payService.confirm(req, userId);

		// when - 새로고침·재전송
		OrderStatus result = payService.confirm(req, userId);

		// then
		assertThat(result).isEqualTo(OrderStatus.PAID);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
		verify(1, postRequestedFor(urlEqualTo("/v1/payments/confirm")));
	}

	@Test
	@DisplayName("결과를 모르는(PENDING) 주문에 같은 paymentKey로 확정을 다시 요청하면, 토스를 부르지 않고 PENDING을 돌려준다")
	void reconfirmPendingOrder_pendingWithoutTossCall() {
		// given - 토스가 처리 중이라 PENDING으로 남은 주문
		Orders order = prepareOrder();
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(409)
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{ "code": "IDEMPOTENT_REQUEST_PROCESSING", "message": "이전 멱등 요청이 처리중이에요." }
					""")));
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());
		payService.confirm(req, userId);

		// when
		OrderStatus result = payService.confirm(req, userId);

		// then
		assertThat(result).isEqualTo(OrderStatus.PAYMENT_PENDING);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
		verify(1, postRequestedFor(urlEqualTo("/v1/payments/confirm")));
	}

	@Test
	@DisplayName("재요청까지 응답이 끊겼고 토스가 예상 밖 상태(WAITING_FOR_DEPOSIT)라고 하면, 주문을 건드리지 않고 PENDING을 돌려준다")
	void responseLost_unexpectedTossStatus_pending() {
		// given - 가상계좌 입금 대기. 이 서비스는 가상계좌를 지원하지 않는다
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());
		stubConfirmConnectionReset();
		stubGetPayment(req.getPaymentKey(), """
			{ "status": "WAITING_FOR_DEPOSIT" }
			""");

		// when
		OrderStatus result = payService.confirm(req, userId);

		// then
		assertThat(result).isEqualTo(OrderStatus.PAYMENT_PENDING);
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
	}

	@Test
	@DisplayName("재요청까지 응답이 끊기고 토스 조회까지 실패(404)하면 PENDING으로 남고, 1분 뒤 보정 스케줄러가 같은 멱등키로 다시 요청해 PAID로 확정한다")
	void responseLost_queryAlsoFails_pendingThenSchedulerPaid() {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());
		stubConfirmConnectionReset();
		stubFor(get(urlEqualTo("/v1/payments/" + req.getPaymentKey()))
			.willReturn(aResponse()
				.withStatus(404)
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{ "code": "NOT_FOUND_PAYMENT", "message": "존재하지 않는 결제 정보 입니다." }
					""")));

		// when 1 - 결제 여부를 알 수 없으므로 임의로 취소/승인하지 않고 보류 상태로 둔다
		OrderStatus result = payService.confirm(req, userId);

		// then 1
		assertThat(result).isEqualTo(OrderStatus.PAYMENT_PENDING);
		assertThat(orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow().getOrderStatus())
			.isEqualTo(OrderStatus.PAYMENT_PENDING);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);

		// when 2 - 토스가 회복된 뒤 1분이 지나 스케줄러가 실행된다
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{ "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
					""")));
		markUpdatedAt(order, LocalDateTime.now().minusMinutes(2));
		paymentRecoveryScheduler.recoverPendingPayments();

		// then 2 - 재고는 이미 차감돼 있으므로 그대로
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.PAID);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
		verify(3, postRequestedFor(urlEqualTo("/v1/payments/confirm"))
			.withHeader("Idempotency-Key", equalTo(order.getOrderNumber())));
	}

	@Test
	@DisplayName("확정 요청과 보정 스케줄러가 같은 주문에 동시에 거절을 받아도, 취소와 재고 복원은 1번만 일어난다")
	void confirmAndSchedulerConcurrently_appliedOnce() throws Exception {
		// given - 확정 요청이 PENDING으로 바꾸고 토스 응답을 기다리는 동안 1분이 지나, 스케줄러가 같은 주문을 꺼낸 상황.
		// 거절로 확인하는 이유: 성공 반영은 재고를 건드리지 않아 두 번 반영돼도 드러나지 않지만, 재고 복원은 두 번이면 재고가 늘어난다
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(400)
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{ "code": "REJECT_CARD_PAYMENT", "message": "한도초과 혹은 잔액부족으로 결제에 실패했습니다." }
					""")));

		CountDownLatch confirmInFlight = new CountDownLatch(1);
		CountDownLatch bothRequested = new CountDownLatch(2);
		AtomicInteger overlapped = new AtomicInteger();
		doAnswer(invocation -> {
			if (confirmInFlight.getCount() == 1) { // 첫 호출은 확정 요청
				markUpdatedAt(order, LocalDateTime.now().minusMinutes(2));
				confirmInFlight.countDown();
			}
			// 두 승인 요청이 모두 토스에 도착한 뒤 응답을 받게 한다 (같은 멱등키라 토스는 같은 응답을 돌려준다)
			bothRequested.countDown();
			if (bothRequested.await(5, TimeUnit.SECONDS)) {
				overlapped.incrementAndGet();
			}
			return invocation.callRealMethod();
		}).when(tossPaymentClient).confirm(any());

		// when
		ExecutorService pool = Executors.newFixedThreadPool(2);
		Future<OrderStatus> confirmResult = pool.submit(() -> payService.confirm(req, userId));
		Future<?> schedulerRun = pool.submit(() -> {
			confirmInFlight.await();
			paymentRecoveryScheduler.recoverPendingPayments();
			return null;
		});

		// then
		assertThat(confirmResult.get(30, TimeUnit.SECONDS)).isEqualTo(OrderStatus.CANCELED);
		schedulerRun.get(30, TimeUnit.SECONDS);
		pool.shutdown();

		assertThat(overlapped.get()).as("두 승인 요청이 겹쳤는지").isEqualTo(2);
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
		assertThat(stock()).isEqualTo(INITIAL_STOCK);
		Mockito.verify(tossPaymentClient, times(2)).confirm(any()); // 확정 요청 1번 + 스케줄러 1번
		Mockito.verify(productJdbcRepository, times(1)).updateOptionStock(anyMap(), eq(false));
		Mockito.verify(productJdbcRepository, times(1)).updateOptionStock(anyMap(), eq(true));
	}

	// 토스가 요청을 받은 뒤 응답 도중 연결이 끊기는 상황
	private void stubConfirmConnectionReset() {
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
	}

	// 첫 승인 요청은 firstResponse, 그다음 요청(재요청)은 200 승인
	private void stubConfirmFirstThen(ResponseDefinitionBuilder firstResponse) {
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.inScenario("confirm-retry")
			.whenScenarioStateIs(Scenario.STARTED)
			.willReturn(firstResponse)
			.willSetStateTo("retried"));
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.inScenario("confirm-retry")
			.whenScenarioStateIs("retried")
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{ "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
					""")));
	}

	private void stubGetPayment(String paymentKey, String body) {
		stubFor(get(urlEqualTo("/v1/payments/" + paymentKey))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody(body)));
	}
}
