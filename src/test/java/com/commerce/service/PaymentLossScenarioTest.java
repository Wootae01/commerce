package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.commerce.common.enums.OrderStatus;
import com.commerce.order.domain.Orders;
import com.commerce.payment.dto.PayConfirmDTO;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import lombok.extern.slf4j.Slf4j;

/**
 * 응답 유실 4시나리오 × 3버전 비교 (#17) — ③ 멱등키 재요청 + 보정 스케줄러.
 * ① bb4f119(보정 없음), ② 21f9d0f(조회만)에도 같은 시나리오의 테스트가 있고, 마지막 단계만 다르다.
 *
 * <pre>
 * 1. 장애 구간: 승인 요청의 응답이 끊긴다(재요청도 끊김). 결제 조회는 된다
 * 2. 장애가 풀린다. 처리 중이던 결제는 토스에서 승인이 끝난다
 * 3. 버전별 마지막 단계 — ③: 1분 뒤 보정 스케줄러 실행
 *
 * 시나리오    토스에서 일어난 일                         조회(paymentKey / 주문번호)     장애 뒤 같은 멱등키로 승인 요청
 * 승인됨      승인 요청이 도착해 승인됨                   DONE / DONE                    200 (같은 응답 재생)
 * 거절        승인 요청이 도착해 거절됨                   ABORTED / ABORTED              400 거절
 * 처리 중     승인 요청이 도착해 처리 중, 장애 뒤 승인    IN_PROGRESS / IN_PROGRESS      200
 * 미도착      승인 요청이 도착하지 않음(인증만 된 상태)   IN_PROGRESS / 404 (#10 확인)   200 (이때 처음 승인)
 * </pre>
 * 결과 줄([결과])을 모아 시나리오×버전 표를 만든다.
 */
@Slf4j
class PaymentLossScenarioTest extends PaymentIntegrationTestSupport {

	private static final String TOSS = "toss";
	private static final String RECOVERED = "recovered";

	// 기대값은 세 버전 모두 같다: 토스 결과와 주문 상태가 맞아야 하고(승인이면 PAID, 아니면 CANCELED), 결과를 모른 채 남으면 안 된다
	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = {"승인됨", "거절", "처리 중", "미도착"})
	void lossScenario(String scenario) {
		// given
		Orders order = prepareOrder();
		PayConfirmDTO req = new PayConfirmDTO("pk-" + order.getOrderNumber(), order.getOrderNumber(), order.getFinalPrice());
		stubToss(scenario, req);

		// when 1 - 장애 구간에 확정 요청
		try {
			payService.confirm(req, userId);
		} catch (Exception e) {
			log.info("확정 요청 예외: {}", e.toString());
		}

		// when 2 - 장애가 풀린다
		setScenarioState(TOSS, RECOVERED);

		// when 3 - ③의 마지막 단계: 1분 뒤 보정 스케줄러
		markUpdatedAt(order, LocalDateTime.now().minusMinutes(2));
		paymentRecoveryScheduler.recoverPendingPayments();

		// then
		OrderStatus status = statusOf(order);
		boolean tossApproved = isTossApproved(scenario);
		log.info("[결과] ③ 멱등키 재요청+스케줄러 | {} | {} | 재고 {} | 토스 {} | {}",
			scenario, status, stock(), tossApproved ? "승인" : "미승인", consistency(status, tossApproved));
		assertThat(consistency(status, tossApproved))
			.as("토스 %s, 주문 %s", tossApproved ? "승인" : "미승인", status)
			.isEqualTo("일치");
		assertThat(stock()).as("재고").isEqualTo(status == OrderStatus.PAID ? INITIAL_STOCK - 1 : INITIAL_STOCK);
	}

	// ---- 아래는 세 버전 공통 (시나리오별 토스 응답, 결과 판정) ----

	private void stubToss(String scenario, PayConfirmDTO req) {
		String queryStatus = switch (scenario) {
			case "승인됨" -> "DONE";
			case "거절" -> "ABORTED";
			default -> "IN_PROGRESS";
		};

		// 장애 구간: 승인 요청은 응답이 끊기고, 조회는 된다
		stubFor(post(urlEqualTo("/v1/payments/confirm")).inScenario(TOSS).whenScenarioStateIs(Scenario.STARTED)
			.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
		stubFor(get(urlEqualTo("/v1/payments/" + req.getPaymentKey())).inScenario(TOSS).whenScenarioStateIs(Scenario.STARTED)
			.willReturn(json(200, "{ \"status\": \"" + queryStatus + "\", \"method\": \"카드\", \"approvedAt\": \"2026-01-03T01:23:45+09:00\" }")));
		stubFor(get(urlEqualTo("/v1/payments/orders/" + req.getOrderId())).inScenario(TOSS).whenScenarioStateIs(Scenario.STARTED)
			.willReturn("미도착".equals(scenario)
				? json(404, "{ \"code\": \"NOT_FOUND_PAYMENT\", \"message\": \"존재하지 않는 결제 정보 입니다.\" }")
				: json(200, "{ \"status\": \"" + queryStatus + "\", \"method\": \"카드\", \"approvedAt\": \"2026-01-03T01:23:45+09:00\" }")));

		// 장애 뒤: 같은 멱등키로 승인을 요청하면
		stubFor(post(urlEqualTo("/v1/payments/confirm")).inScenario(TOSS).whenScenarioStateIs(RECOVERED)
			.willReturn("거절".equals(scenario)
				? json(400, "{ \"code\": \"REJECT_CARD_PAYMENT\", \"message\": \"한도초과 혹은 잔액부족으로 결제에 실패했습니다.\" }")
				: json(200, "{ \"method\": \"카드\", \"approvedAt\": \"2026-01-03T01:23:45+09:00\" }")));
	}

	// 토스에서 결국 승인됐는가: 승인됨·처리 중은 토스가 승인을 끝낸다. 미도착은 장애 뒤 승인 요청이 도착해야 승인된다
	private boolean isTossApproved(String scenario) {
		return switch (scenario) {
			case "승인됨", "처리 중" -> true;
			case "미도착" -> getAllServeEvents().stream().anyMatch(event ->
					event.getRequest().getUrl().equals("/v1/payments/confirm")
					&& event.getResponse().getFault() == null && event.getResponse().getStatus() == 200); // 끊긴 응답도 200으로 기록된다
			default -> false;
		};
	}

	private String consistency(OrderStatus status, boolean tossApproved) {
		if (status == OrderStatus.PAYMENT_PENDING) {
			return "미확정(재고 묶임)";
		}
		boolean paid = status == OrderStatus.PAID;
		return paid == tossApproved ? "일치" : "불일치";
	}

	private ResponseDefinitionBuilder json(int status, String body) {
		return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(body);
	}
}
