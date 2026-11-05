package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.commerce.admin.dto.AdminOrderListResponseDTO;
import com.commerce.admin.dto.AdminOrderSearchCond;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.PaymentType;
import com.commerce.order.domain.Orders;
import com.commerce.order.dto.OrderDetailResponseDTO;
import com.commerce.payment.dto.PayConfirmDTO;
import com.commerce.payment.dto.PaySuccessDTO;
import com.commerce.user.domain.User;

import jakarta.persistence.EntityManagerFactory;

/**
 * 결제 정보(결제 수단·승인 시각)를 읽는 곳: 결제 결과 조회, 주문 상세, 관리자 주문 검색.
 * 결제 정보가 주문에서 Payment로 옮겨가도(#23) 같은 값이 보여야 한다.
 */
class PaymentInfoReadTest extends PaymentIntegrationTestSupport {

	@Autowired EntityManagerFactory entityManagerFactory;

	@Test
	@DisplayName("결제가 승인되면 결제 결과에 결제 수단과 승인 시각이 보인다")
	void paid_resultShowsMethodAndApprovedAt() {
		// given
		Orders order = prepareOrder();
		stubConfirm(200, """
			{ "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
			""");
		payService.confirm(confirmRequestOf(order), userId);

		// when
		PaySuccessDTO result = payService.getPaymentResult(order.getOrderNumber(), userId);

		// then
		assertThat(result.getOrderNumber()).isEqualTo(order.getOrderNumber());
		assertThat(result.getAmount()).isEqualTo(order.getFinalPrice());
		assertThat(result.getMethod()).isEqualTo("카드");
		assertThat(result.getApprovedAt()).isEqualTo("2026-01-03 01:23:45");
		assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.PAID);
	}

	@Test
	@DisplayName("승인 결과를 모르면(PENDING) 결제 결과의 결제 수단은 '기타', 승인 시각은 '-'다")
	void pending_resultShowsNoMethodYet() {
		// given - 토스가 같은 멱등키의 요청을 아직 처리 중
		Orders order = prepareOrder();
		stubConfirm(409, """
			{ "code": "IDEMPOTENT_REQUEST_PROCESSING", "message": "이전 멱등 요청이 처리중이에요." }
			""");
		payService.confirm(confirmRequestOf(order), userId);

		// when
		PaySuccessDTO result = payService.getPaymentResult(order.getOrderNumber(), userId);

		// then
		assertThat(result.getMethod()).isEqualTo("기타");
		assertThat(result.getApprovedAt()).isEqualTo("-");
		assertThat(result.getOrderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
	}

	@Test
	@DisplayName("결제한 주문의 상세를 열면 결제 수단이 보인다")
	void paid_orderDetailShowsMethod() {
		// given
		Orders order = prepareOrder();
		stubConfirm(200, """
			{ "method": "간편결제", "approvedAt": "2026-01-03T01:23:45+09:00" }
			""");
		payService.confirm(confirmRequestOf(order), userId);
		User user = userRepository.findById(userId).orElseThrow();

		// when
		OrderDetailResponseDTO detail = orderService.getMyOrderDetail(order.getOrderNumber(), user);

		// then
		assertThat(detail.getPaymentInfo().getPaymentMethod()).isEqualTo("간편결제");
		assertThat(detail.getPaymentInfo().getOrderStatus()).isEqualTo("결제완료");
	}

	@Test
	@DisplayName("관리자가 결제 수단으로 주문을 검색하면 그 결제 수단의 주문만 나오고, 목록에 결제 수단이 보인다")
	void adminSearchByPaymentType() {
		// given
		Orders card = paidOrder("카드");
		paidOrder("간편결제");
		AdminOrderSearchCond cond = new AdminOrderSearchCond();
		cond.setPaymentType(PaymentType.CARD);

		// when
		Page<AdminOrderListResponseDTO> result = orderService.getOrderList(cond, PageRequest.of(0, 20));

		// then
		assertThat(result.getContent())
			.extracting(AdminOrderListResponseDTO::getOrderNumber, AdminOrderListResponseDTO::getPaymentType)
			.containsExactly(tuple(card.getOrderNumber(), "카드"));
	}

	@Test
	@DisplayName("관리자 주문 목록의 쿼리 수는 주문 수와 상관없이 같다(결제 수단을 주문마다 따로 읽지 않는다)")
	void adminSearch_queryCountIndependentOfOrderCount() {
		// given
		paidOrder("카드");
		long withOneOrder = countQueries(() -> orderService.getOrderList(new AdminOrderSearchCond(), PageRequest.of(0, 20)));
		paidOrder("카드");
		paidOrder("간편결제");
		paidOrder("카드");

		// when
		long withFourOrders = countQueries(() -> orderService.getOrderList(new AdminOrderSearchCond(), PageRequest.of(0, 20)));

		// then
		assertThat(withFourOrders).isEqualTo(withOneOrder);
	}

	private long countQueries(Runnable action) {
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();
		action.run();
		return statistics.getPrepareStatementCount();
	}

	// 결제 수단으로 승인된 주문
	private Orders paidOrder(String tossMethod) {
		Orders order = prepareOrder();
		stubConfirm(200, "{ \"method\": \"" + tossMethod + "\", \"approvedAt\": \"2026-01-03T01:23:45+09:00\" }");
		payService.confirm(confirmRequestOf(order), userId);
		return order;
	}

	private PayConfirmDTO confirmRequestOf(Orders order) {
		return new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());
	}

	private void stubConfirm(int status, String body) {
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBody(body)));
	}
}
