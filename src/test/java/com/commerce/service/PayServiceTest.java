package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.wiremock.spring.EnableWireMock;

import com.commerce.config.IntegrationTest;
import com.commerce.domain.Orders;
import com.commerce.domain.Product;
import com.commerce.domain.User;
import com.commerce.domain.enums.OrderStatus;
import com.commerce.domain.enums.OrderType;
import com.commerce.domain.enums.RoleType;
import com.commerce.dto.OrderCreateRequestDTO;
import com.commerce.dto.PayConfirmDTO;
import com.commerce.repository.CartProductRepository;
import com.commerce.repository.CartRepository;
import com.commerce.repository.OrderProductRepository;
import com.commerce.repository.OrderRepository;
import com.commerce.repository.ProductRepository;
import com.commerce.repository.UserRepository;
import com.commerce.util.SecurityUtil;

import lombok.extern.slf4j.Slf4j;

@IntegrationTest
@Slf4j
@EnableWireMock
@TestPropertySource(properties = {
	"toss.base-url=${wiremock.server.baseUrl}"
})
class PayServiceTest {

	@Value("${wiremock.server.baseUrl}")
	String wireMockBaseUrl; // 자동으로 넣어줌

	@Autowired
	private PayService payService;

	@Autowired
	private OrderService orderService;
	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private OrderProductRepository orderProductRepository;

	@Autowired ProductRepository productRepository;

	@MockitoBean SecurityUtil securityUtil;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CartService cartService;
	@Autowired
	private CartRepository cartRepository;
	@Autowired
	private CartProductRepository cartProductRepository;

	@BeforeEach
	void stubs() {
		stubFor(post(urlEqualTo("/v1/payments/confirm"))
			.willReturn(aResponse()
				.withStatus(200)
				.withFixedDelay(600)
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{
					  "method": "카드",
					  "approvedAt": "2026-01-03T01:23:45+09:00"
					}
					""")));
	}

	@AfterEach
	void cleanUp() {
		orderProductRepository.deleteAllInBatch(); // orders, product를 물고 있음(자식)
		orderRepository.deleteAllInBatch();        // user를 물고 있음(자식)
		cartProductRepository.deleteAllInBatch();
		cartRepository.deleteAllInBatch();
		productRepository.deleteAllInBatch();      // 더 이상 참조 없을 때
		userRepository.deleteAllInBatch();         // orders가 먼저 삭제되어야 안전
	}

	@Test
	@DisplayName("pay confirm 카트 주문 테스트")
	void payConfirmByCartOrder() {
		// given
		// 사용자 생성
		User user = User.builder()
			.customerPaymentKey("customerPaymentKey")
			.email("c@naver.com")
			.name("홍길동")
			.phone("01012345678")
			.role(RoleType.ROLE_USER)
			.username("username")
			.build();
		user = userRepository.save(user);

		Mockito.when(securityUtil.getCurrentUser()).thenReturn(user);

		int stock = 100;
		int quantity = 1;

		// 상품 생성
		Product product = new Product();
		product.update(1000, stock, "상품1", "설명");
		product = productRepository.save(product);

		// 카트 담기
		cartService.addCart(product.getId(), quantity);

		// 주문 준비
		OrderCreateRequestDTO dto = new OrderCreateRequestDTO();
		dto.setName("홍길동");
		dto.setPhone("01012345678");
		dto.setAddress("서울");
		dto.setAddressDetail("어딘가");
		dto.setOrderType(OrderType.CART);
		dto.setProductId(product.getId());
		dto.setQuantity(quantity);

		Orders orders = orderService.prepareOrderFromBuyNow(dto);

		String orderNumber = orders.getOrderNumber();
		String paymentKey = UUID.randomUUID().toString();
		int amount = orders.getFinalPrice();



		PayConfirmDTO payConfirmDTO = new PayConfirmDTO(paymentKey, orderNumber, amount);

		// when
		payService.confirm(payConfirmDTO,user.getId());

		// then
		Product afterProduct = productRepository.findById(product.getId()).orElseThrow();
		Orders afterOrder = orderRepository.findByOrderNumber(orderNumber).orElseThrow();

		assertThat(afterProduct.getStock()).isEqualTo(stock - quantity);
		assertThat(afterOrder.getOrderStatus()).isEqualTo(OrderStatus.PAID);
		assertThat(afterOrder.getApprovedAt()).isEqualTo(LocalDateTime.of(2026, 1, 3, 1, 23, 45));
	}


	@Test
	@DisplayName("payConfirm 메서드 동시성 테스트")
	void concurrencyPayConfirm() throws InterruptedException {

		// given
		// 사용자 생성
		User user = User.builder()
			.customerPaymentKey("customerPaymentKey")
			.email("c@naver.com")
			.name("홍길동")
			.phone("01012345678")
			.role(RoleType.ROLE_USER)
			.username("username")
			.build();
		user = userRepository.save(user);

		Mockito.when(securityUtil.getCurrentUser()).thenReturn(user);


		// 상품 생성
		Product product = new Product();
		product.update(1000, 100, "상품1", "설명");
		product = productRepository.save(product);

		// 주문 준비
		OrderCreateRequestDTO dto = new OrderCreateRequestDTO();
		dto.setName("홍길동");
		dto.setPhone("01012345678");
		dto.setAddress("서울");
		dto.setAddressDetail("어딘가");
		dto.setOrderType(OrderType.BUY_NOW);
		dto.setProductId(product.getId());
		dto.setQuantity(1);

		int n = 50;

		// 서로 다른 주문 준비
		List<PayConfirmDTO> reqs = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Orders orders = orderService.prepareOrderFromBuyNow(dto);

			String orderNumber = orders.getOrderNumber();
			String paymentKey = "test_payment_key_concurrency" + i;
			int amount = orders.getFinalPrice();

			reqs.add(new PayConfirmDTO(paymentKey, orderNumber, amount));
		}

		// when
		ExecutorService pool = Executors.newFixedThreadPool(32);
		CountDownLatch done = new CountDownLatch(n);
		AtomicInteger fail = new AtomicInteger();

		for (int i = 0; i < n; i++) {
			final int index = i;
			final Long userId = user.getId();

			pool.submit(() -> {
				try {
					payService.confirm(reqs.get(index), userId);
				} catch (Exception e) {
					fail.incrementAndGet();
					log.error("pay confirm 실패", e);
				} finally {
					done.countDown();
				}
			});
		}

		boolean finished = done.await(60, TimeUnit.SECONDS);
		pool.awaitTermination(10, TimeUnit.SECONDS);
		pool.shutdown();


		// then
		Product product1 = productRepository.findById(product.getId())
			.orElseThrow();
		List<Orders> all = orderRepository.findAll();
		assertThat(finished).isTrue();
		assertThat(fail.get()).isEqualTo(0);
		assertThat(product1.getStock()).isEqualTo(50);

		for (Orders orders : all) {
			assertThat(orders.getOrderStatus()).isEqualTo(OrderStatus.PAID);
			assertThat(orders.getApprovedAt()).isEqualTo(LocalDateTime.of(2026, 1, 3, 1, 23, 45));
		}

	}

	@Test
	@DisplayName("재고 부족 시 결제 보상 처리 - 자동 환불")
	void compensateWhenStockExhausted() {
		// given
		User user = User.builder()
			.customerPaymentKey("customerPaymentKey")
			.email("c@naver.com")
			.name("홍길동")
			.phone("01012345678")
			.role(RoleType.ROLE_USER)
			.username("username")
			.build();
		user = userRepository.save(user);
		Long userId = user.getId();
		Mockito.when(securityUtil.getCurrentUser()).thenReturn(user);

		// 재고 1인 상품 생성
		Product product = new Product();
		product.update(1000, 1, "상품1", "설명");
		product = productRepository.save(product);

		// 두 개 주문 준비
		OrderCreateRequestDTO dto = new OrderCreateRequestDTO();
		dto.setName("홍길동");
		dto.setPhone("01012345678");
		dto.setAddress("서울");
		dto.setAddressDetail("어딘가");
		dto.setOrderType(OrderType.BUY_NOW);
		dto.setProductId(product.getId());
		dto.setQuantity(1);

		Orders order1 = orderService.prepareOrderFromBuyNow(dto);
		Orders order2 = orderService.prepareOrderFromBuyNow(dto);

		// 토스 취소 stub
		stubFor(post(urlPathMatching("/v1/payments/.*/cancel"))
			.willReturn(aResponse()
				.withStatus(200)
				.withHeader("Content-Type", "application/json")
				.withBody("""
					{
					  "cancels": [{"cancelAmount": %d}],
					  "method": "카드"
					}
					""".formatted(order2.getFinalPrice()))));

		// when - 첫 번째 주문은 성공
		PayConfirmDTO req1 = new PayConfirmDTO(
			UUID.randomUUID().toString(), order1.getOrderNumber(), order1.getFinalPrice());
		payService.confirm(req1, user.getId());

		// 두 번째 주문은 재고 부족 -> 보상 처리
		PayConfirmDTO req2 = new PayConfirmDTO(
			UUID.randomUUID().toString(), order2.getOrderNumber(), order2.getFinalPrice());

		assertThatThrownBy(() -> payService.confirm(req2, userId))
			.isInstanceOf(ResponseStatusException.class)
			.satisfies(ex -> {
				ResponseStatusException rse = (ResponseStatusException) ex;
				assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			});

		// then
		Orders canceledOrder = orderRepository.findByOrderNumber(order2.getOrderNumber()).orElseThrow();
		assertThat(canceledOrder.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
		assertThat(canceledOrder.getPaymentKey()).isEqualTo(req2.getPaymentKey());

		// 토스 cancel API 호출 확인
		verify(postRequestedFor(urlPathMatching("/v1/payments/.*/cancel")));
	}

	@Test
	@DisplayName("재고 부족 후 토스 취소도 실패 - REFUND_FAILED 상태")
	void compensateFailsWhenTossCancelFails() {
		// given
		User user = User.builder()
			.customerPaymentKey("customerPaymentKey")
			.email("test@naver.com")
			.name("테스트")
			.phone("01099998888")
			.role(RoleType.ROLE_USER)
			.username("testuser")
			.build();
		user = userRepository.save(user);
		Long userId = user.getId();
		Mockito.when(securityUtil.getCurrentUser()).thenReturn(user);

		Product product = new Product();
		product.update(1000, 1, "상품1", "설명");
		product = productRepository.save(product);

		OrderCreateRequestDTO dto = new OrderCreateRequestDTO();
		dto.setName("테스트");
		dto.setPhone("01099998888");
		dto.setAddress("서울");
		dto.setAddressDetail("어딘가");
		dto.setOrderType(OrderType.BUY_NOW);
		dto.setProductId(product.getId());
		dto.setQuantity(1);

		Orders order1 = orderService.prepareOrderFromBuyNow(dto);
		Orders order2 = orderService.prepareOrderFromBuyNow(dto);

		// 토스 취소 - 500 에러 반환
		stubFor(post(urlPathMatching("/v1/payments/.*/cancel"))
			.willReturn(aResponse()
				.withStatus(500)
				.withHeader("Content-Type", "application/json")
				.withBody("{\"message\": \"Internal Server Error\"}")));

		// when - 첫 번째 성공
		PayConfirmDTO req1 = new PayConfirmDTO(
			UUID.randomUUID().toString(), order1.getOrderNumber(), order1.getFinalPrice());
		payService.confirm(req1, user.getId());

		// 두 번째 - 재고 부족 + 토스 취소도 실패
		PayConfirmDTO req2 = new PayConfirmDTO(
			UUID.randomUUID().toString(), order2.getOrderNumber(), order2.getFinalPrice());

		assertThatThrownBy(() -> payService.confirm(req2, userId))
			.isInstanceOf(ResponseStatusException.class);

		// then - REFUND_FAILED 상태, paymentKey 저장됨
		Orders failedOrder = orderRepository.findByOrderNumber(order2.getOrderNumber()).orElseThrow();
		assertThat(failedOrder.getOrderStatus()).isEqualTo(OrderStatus.REFUND_FAILED);
		assertThat(failedOrder.getPaymentKey()).isEqualTo(req2.getPaymentKey());
	}

}