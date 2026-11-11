package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.commerce.common.enums.OrderStatus;
import com.commerce.order.domain.Orders;
import com.commerce.payment.dto.CancelResponseDTO;
import com.github.tomakehurst.wiremock.http.Fault;

/**
 * 보정 스케줄러의 취소 보정: CANCEL_REQUESTED(취소 처리 중)가 된 지 1분 지난 주문을 같은 멱등키로 다시 취소 요청해 끝낸다.
 * 스케줄러 실행 메서드를 직접 호출하고, 시간 조건은 updated_at을 과거로 바꿔 만든다.
 */
@ExtendWith(OutputCaptureExtension.class)
class CancelRecoverySchedulerTest extends PaymentIntegrationTestSupport {

	@Test
	@DisplayName("취소 처리 중이 된 지 1분 지난 주문은 같은 멱등키로 1번 취소를 다시 요청하고, 200이면 CANCELED·재고 복원으로 끝낸다")
	void cancelRequestedOverOneMinute_canceled() {
		// given
		Orders order = cancelRequestedOrder();
		markUpdatedAt(order, LocalDateTime.now().minusMinutes(2));
		stubCancel(order, 200, "{ \"status\": \"CANCELED\", \"method\": \"카드\" }");

		// when
		paymentRecoveryScheduler.recoverCancelProcessing();

		// then
		assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELED);
		assertThat(stock()).as("재고 복원").isEqualTo(INITIAL_STOCK);
		verify(1, postRequestedFor(urlEqualTo(cancelUrlOf(order)))
			.withHeader("Idempotency-Key", equalTo(order.getOrderNumber()))
			.withRequestBody(matchingJsonPath("$.cancelReason", equalTo("단순 변심"))));
	}

	@Test
	@DisplayName("다시 요청한 취소가 400 ALREADY_CANCELED_PAYMENT면(토스에서는 이미 환불됨) CANCELED·재고 복원으로 끝낸다")
	void alreadyCanceled_canceled() {
		// given
		Orders order = cancelRequestedOrder();
		markUpdatedAt(order, LocalDateTime.now().minusMinutes(2));
		stubCancel(order, 400, "{ \"code\": \"ALREADY_CANCELED_PAYMENT\", \"message\": \"이미 취소된 결제 입니다.\" }");

		// when
		paymentRecoveryScheduler.recoverCancelProcessing();

		// then
		assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELED);
		assertThat(stock()).as("재고 복원").isEqualTo(INITIAL_STOCK);
	}

	@Test
	@DisplayName("다시 요청한 취소를 토스가 거절(4xx)하면 PAID로 되돌리고 재고는 그대로 두며, 에러 로그를 남긴다")
	void rejected_revertedToPaid(CapturedOutput output) {
		// given
		Orders order = cancelRequestedOrder();
		markUpdatedAt(order, LocalDateTime.now().minusMinutes(2));
		stubCancel(order, 403, "{ \"code\": \"NOT_CANCELABLE_PAYMENT\", \"message\": \"취소 할 수 없는 결제 입니다.\" }");

		// when
		paymentRecoveryScheduler.recoverCancelProcessing();

		// then - 사용자에게 알릴 곳이 없으므로 로그로 남긴다
		assertThat(statusOf(order)).isEqualTo(OrderStatus.PAID);
		assertThat(stock()).as("재고").isEqualTo(INITIAL_STOCK - 1);
		assertThat(logLines(output, "토스 취소 실패", order)).isEqualTo(1);
	}

	@Test
	@DisplayName("다시 요청한 취소를 토스가 아직 처리 중(409)이라고 하면 취소 처리 중으로 두고, 다음 실행에서 다시 요청한다")
	void processing_keptCancelRequested() {
		// given
		Orders order = cancelRequestedOrder();
		markUpdatedAt(order, LocalDateTime.now().minusMinutes(2));
		stubCancel(order, 409, "{ \"code\": \"IDEMPOTENT_REQUEST_PROCESSING\", \"message\": \"이전 멱등 요청이 처리중이에요.\" }");

		// when
		paymentRecoveryScheduler.recoverCancelProcessing();
		paymentRecoveryScheduler.recoverCancelProcessing();

		// then
		assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCEL_REQUESTED);
		assertThat(stock()).as("재고").isEqualTo(INITIAL_STOCK - 1);
		verify(2, postRequestedFor(urlEqualTo(cancelUrlOf(order))));
	}

	@Test
	@DisplayName("취소 처리 중이 된 지 1분이 안 된 주문과 취소 처리 중이 아닌 주문은 건드리지 않는다")
	void notTargets_untouched() {
		// given
		Orders recent = cancelRequestedOrder(); // 방금 취소 처리 중이 됨
		Orders paid = paidOrder();
		markUpdatedAt(paid, LocalDateTime.now().minusMinutes(2));
		resetAllRequests();

		// when
		paymentRecoveryScheduler.recoverCancelProcessing();

		// then
		verify(0, postRequestedFor(urlMatching("/v1/payments/.*/cancel")));
		assertThat(statusOf(recent)).isEqualTo(OrderStatus.CANCEL_REQUESTED);
		assertThat(statusOf(paid)).isEqualTo(OrderStatus.PAID);
	}

	@Test
	@DisplayName("토스와 통신하지 못한 주문(연결 실패·5xx)이 3건 연속이면 그 실행을 멈춘다")
	void threeConsecutiveUnreachable_stopRun() {
		// given - 오래된 순 o1..o4. 연결 실패, 5xx, 연결 실패, 연결 실패
		List<Orders> orders = cancelRequestedOrdersOldestFirst(4);
		stubFor(post(urlEqualTo(cancelUrlOf(orders.get(0))))
			.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
		stubCancel(orders.get(1), 500, "{ \"code\": \"FAILED_INTERNAL_SYSTEM_PROCESSING\", \"message\": \"오류\" }");
		for (int i : List.of(2, 3)) {
			stubFor(post(urlEqualTo(cancelUrlOf(orders.get(i))))
				.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
		}

		// when
		paymentRecoveryScheduler.recoverCancelProcessing();

		// then - 바로 재요청하지 않으므로 주문마다 1번, o4는 요청하지 않는다
		for (int i = 0; i < 3; i++) {
			verify(1, postRequestedFor(urlEqualTo(cancelUrlOf(orders.get(i)))));
		}
		verify(0, postRequestedFor(urlEqualTo(cancelUrlOf(orders.get(3)))));
		orders.forEach(order -> assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCEL_REQUESTED));
	}

	@Test
	@DisplayName("취소 결과를 24시간 넘게 모르는 주문은 자동 처리에서 빼고(취소 요청 안 함), 에러 로그를 한 번만 남긴다")
	void unknownOver24Hours_excludedAndErrorLoggedOnce(CapturedOutput output) {
		// given
		Orders expired = cancelRequestedOrder();
		markUpdatedAt(expired, LocalDateTime.now().minusHours(25));
		stubCancel(expired, 200, "{ \"status\": \"CANCELED\", \"method\": \"카드\" }");

		// when
		paymentRecoveryScheduler.recoverCancelProcessing();
		paymentRecoveryScheduler.recoverCancelProcessing();

		// then - 사람이 확인할 때까지 취소 처리 중으로 두고 재고도 그대로
		verify(0, postRequestedFor(urlEqualTo(cancelUrlOf(expired))));
		assertThat(statusOf(expired)).isEqualTo(OrderStatus.CANCEL_REQUESTED);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
		assertThat(logLines(output, "취소 결과를 24시간 넘게", expired)).isEqualTo(1);
	}

	// 취소 처리 중 주문 n건. 앞쪽일수록 오래됐다(10분 전, 9분 전, ...)
	private List<Orders> cancelRequestedOrdersOldestFirst(int n) {
		List<Orders> orders = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Orders order = cancelRequestedOrder();
			markUpdatedAt(order, LocalDateTime.now().minusMinutes(10 - i));
			orders.add(order);
		}
		return orders;
	}

	// 이 주문번호가 들어간 로그 중 keyword가 있는 줄 수
	private long logLines(CapturedOutput output, String keyword, Orders order) {
		return output.getOut().lines()
			.filter(line -> line.contains(keyword) && line.contains(order.getOrderNumber()))
			.count();
	}

	// 사용자의 취소 요청이 두 번 다 끊겨 취소 처리 중으로 남은 주문
	private Orders cancelRequestedOrder() {
		Orders order = paidOrder();
		stubFor(post(urlEqualTo(cancelUrlOf(order)))
			.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
		CancelResponseDTO response = payService.cancel(order.getOrderNumber(), userId);
		assertThat(response.getOrderStatus()).as("준비: 취소 처리 중").isEqualTo(OrderStatus.CANCEL_REQUESTED);
		resetAllRequests();
		return order;
	}
}
