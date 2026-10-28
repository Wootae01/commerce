package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.wiremock.spring.EnableWireMock;

import com.commerce.cart.repository.CartProductRepository;
import com.commerce.cart.repository.CartRepository;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.OrderType;
import com.commerce.common.enums.RoleType;
import com.commerce.common.util.SecurityUtil;
import com.commerce.config.IntegrationTest;
import com.commerce.order.domain.Orders;
import com.commerce.order.dto.OrderCreateRequestDTO;
import com.commerce.order.repository.OrderProductRepository;
import com.commerce.order.repository.OrderRepository;
import com.commerce.order.service.OrderService;
import com.commerce.payment.dto.PayConfirmDTO;
import com.commerce.payment.external.TossPaymentClient;
import com.commerce.payment.scheduler.PaymentRecoveryScheduler;
import com.commerce.payment.service.PayService;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.repository.ProductJdbcRepository;
import com.commerce.product.repository.ProductOptionRepository;
import com.commerce.product.repository.ProductRepository;
import com.commerce.user.domain.User;
import com.commerce.user.repository.UserRepository;

/**
 * 보정 스케줄러: PAYMENT_PENDING이 된 지 1분 지난 주문을 같은 멱등키로 다시 승인 요청해 확정한다.
 * 스케줄러 실행 메서드를 직접 호출하고, 시간 조건은 updated_at을 과거로 바꿔 만든다.
 * (PayConsistencyTest와 같은 설정이라 스프링 컨텍스트를 같이 쓴다)
 */
@IntegrationTest
@EnableWireMock
@TestPropertySource(properties = {
	"toss.base-url=${wiremock.server.baseUrl}"
})
class PaymentRecoverySchedulerTest {

	private static final int INITIAL_STOCK = 10;

	@Autowired PaymentRecoveryScheduler paymentRecoveryScheduler;
	@Autowired PayService payService;
	@Autowired OrderService orderService;
	@Autowired OrderRepository orderRepository;
	@Autowired OrderProductRepository orderProductRepository;
	@Autowired ProductRepository productRepository;
	@Autowired ProductOptionRepository productOptionRepository;
	@Autowired UserRepository userRepository;
	@Autowired CartRepository cartRepository;
	@Autowired CartProductRepository cartProductRepository;
	@Autowired JdbcTemplate jdbcTemplate;

	@MockitoBean SecurityUtil securityUtil;
	@MockitoSpyBean ProductJdbcRepository productJdbcRepository;
	@MockitoSpyBean TossPaymentClient tossPaymentClient;

	private Long userId;
	private Long productId;
	private Long optionId;

	@BeforeEach
	void setUp() {
		User user = userRepository.save(User.builder()
			.customerPaymentKey("customerPaymentKey")
			.email("c@naver.com")
			.name("홍길동")
			.phone("01012345678")
			.role(RoleType.ROLE_USER)
			.username("username")
			.build());
		when(securityUtil.getCurrentUser()).thenReturn(user);
		userId = user.getId();

		Product product = new Product();
		product.update(1000, "상품1", "설명");
		ProductOption option = new ProductOption("단품", INITIAL_STOCK, 0);
		product.addOption(option);
		productId = productRepository.save(product).getId();
		optionId = option.getId();
	}

	@AfterEach
	void cleanUp() {
		orderProductRepository.deleteAllInBatch();
		orderRepository.deleteAllInBatch();
		cartProductRepository.deleteAllInBatch();
		cartRepository.deleteAllInBatch();
		productOptionRepository.deleteAllInBatch();
		productRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

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

	private Orders prepareOrder() {
		OrderCreateRequestDTO dto = new OrderCreateRequestDTO();
		dto.setName("홍길동");
		dto.setPhone("01012345678");
		dto.setAddress("서울");
		dto.setAddressDetail("어딘가");
		dto.setOrderType(OrderType.BUY_NOW);
		dto.setProductId(productId);
		dto.setOptionId(optionId);
		dto.setQuantity(1);
		return orderService.prepareOrderFromBuyNow(dto);
	}

	// 주문마다 paymentKey를 주문번호에서 만들어, 확정 요청과 스케줄러 재요청의 값을 비교할 수 있게 한다
	private PayConfirmDTO confirmRequestOf(Orders order) {
		String paymentKey = UUID.nameUUIDFromBytes(order.getOrderNumber().getBytes()).toString();
		return new PayConfirmDTO(paymentKey, order.getOrderNumber(), order.getFinalPrice());
	}

	private void markUpdatedAt(Orders order, LocalDateTime updatedAt) {
		jdbcTemplate.update("update orders set updated_at = ? where order_number = ?",
			updatedAt, order.getOrderNumber());
	}

	private OrderStatus statusOf(Orders order) {
		return orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow().getOrderStatus();
	}

	private int stock() {
		return productOptionRepository.findById(optionId).orElseThrow().getStock();
	}

	private void stubConfirm(int status, String body) {
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(status)
				.withHeader("Content-Type", "application/json")
				.withBody(body)));
	}
}
