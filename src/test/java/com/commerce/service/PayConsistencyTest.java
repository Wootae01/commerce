package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.reactive.function.client.WebClientRequestException;
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
import com.commerce.common.exception.ApiException;
import com.commerce.common.code.ExternalResponseCode;
import com.commerce.payment.external.TossPaymentClient;
import com.commerce.payment.service.PayService;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.repository.ProductJdbcRepository;
import com.commerce.product.repository.ProductOptionRepository;
import com.commerce.product.repository.ProductRepository;
import com.commerce.user.domain.User;
import com.commerce.user.repository.UserRepository;
import com.github.tomakehurst.wiremock.http.Fault;

import lombok.extern.slf4j.Slf4j;

/**
 * 결제 확정 정합성 테스트
 * 1) 같은 주문에 확정 요청이 동시에 2건 들어오는 경우
 * 2) 토스 승인 요청 후 응답이 끊기는 경우
 */
@IntegrationTest
@Slf4j
@EnableWireMock
@TestPropertySource(properties = {
	"toss.base-url=${wiremock.server.baseUrl}"
})
class PayConsistencyTest {

	private static final int INITIAL_STOCK = 10;

	@Autowired PayService payService;
	@Autowired OrderService orderService;
	@Autowired OrderRepository orderRepository;
	@Autowired OrderProductRepository orderProductRepository;
	@Autowired ProductRepository productRepository;
	@Autowired ProductOptionRepository productOptionRepository;
	@Autowired UserRepository userRepository;
	@Autowired CartRepository cartRepository;
	@Autowired CartProductRepository cartProductRepository;

	@MockitoBean SecurityUtil securityUtil;
	@MockitoSpyBean ProductJdbcRepository productJdbcRepository;
	@MockitoSpyBean TossPaymentClient tossPaymentClient;

	private Long userId;
	private Long productId;
	private Long optionId;

	@BeforeEach
	void setUp() {
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(200)
				.withFixedDelay(600)
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{ "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
					""")));

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
	@DisplayName("토스 승인 후 응답이 끊기면, 토스 결제 조회(DONE)로 주문을 PAID 처리한다")
	void responseLost_tossApproved_paid() {
		// given
		Orders order = prepareOrder();
		stubConfirmConnectionReset();
		stubGetPayment(order.getOrderNumber(), """
			{ "status": "DONE", "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
			""");

		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());

		// when
		assertThatThrownBy(() -> payService.confirm(req, userId))
			.isInstanceOf(WebClientRequestException.class);

		// then - 토스에서는 결제 완료 → 우리 주문도 PAID, 재고는 차감된 상태 유지
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.PAID);
		assertThat(after.getPaymentKey()).isEqualTo(req.getPaymentKey());
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
		verify(1, getRequestedFor(urlEqualTo("/v1/payments/orders/" + order.getOrderNumber())));
	}

	@Test
	@DisplayName("응답이 끊겼고 토스도 미승인이면, 재고를 복원하고 주문을 CANCELED 처리한다")
	void responseLost_tossNotApproved_restored() {
		// given
		Orders order = prepareOrder();
		stubConfirmConnectionReset();
		stubGetPayment(order.getOrderNumber(), """
			{ "status": "ABORTED" }
			""");

		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());

		// when
		assertThatThrownBy(() -> payService.confirm(req, userId))
			.isInstanceOf(WebClientRequestException.class);

		// then
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
		assertThat(stock()).isEqualTo(INITIAL_STOCK);
	}

	@Disabled("토스 조회 실패 시 PAYMENT_PENDING 유지 — 대사 작업 미구현")
	@Test
	@DisplayName("[한계] 응답이 끊기고 토스 조회까지 실패하면 PAYMENT_PENDING으로 남는다")
	void responseLost_queryAlsoFails_pending() {
		// given
		Orders order = prepareOrder();
		stubConfirmConnectionReset();
		stubFor(get(urlEqualTo("/v1/payments/orders/" + order.getOrderNumber()))
			.willReturn(aResponse().withStatus(500)));

		PayConfirmDTO req = new PayConfirmDTO(UUID.randomUUID().toString(), order.getOrderNumber(), order.getFinalPrice());

		// when
		assertThatThrownBy(() -> payService.confirm(req, userId))
			.isInstanceOf(WebClientRequestException.class);

		// then - 결제 여부를 알 수 없으므로 임의로 취소/승인하지 않고 보류 상태로 둔다 (대사 작업 대상)
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
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

	private int stock() {
		return productOptionRepository.findById(optionId).orElseThrow().getStock();
	}

	// 토스가 요청을 받은 뒤 응답 도중 연결이 끊기는 상황
	private void stubConfirmConnectionReset() {
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
	}

	private void stubGetPayment(String orderNumber, String body) {
		stubFor(get(urlEqualTo("/v1/payments/orders/" + orderNumber))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody(body)));
	}
}
