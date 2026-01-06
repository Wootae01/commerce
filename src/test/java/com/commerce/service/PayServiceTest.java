package com.commerce.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.wiremock.spring.EnableWireMock;

import com.commerce.config.IntegrationTest;
import com.commerce.domain.Orders;
import com.commerce.domain.Product;
import com.commerce.domain.User;
import com.commerce.domain.enums.OrderType;
import com.commerce.domain.enums.RoleType;
import com.commerce.dto.OrderCreateRequestDTO;
import com.commerce.dto.PayConfirmDTO;
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

	@Autowired ProductRepository productRepository;

	@MockitoBean SecurityUtil securityUtil;
	@Autowired
	private UserRepository userRepository;

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
			String paymentKey = "test_payment_key_" + i;
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
		assertThat(finished).isTrue();
		assertThat(fail.get()).isEqualTo(0);
		assertThat(product1.getStock()).isEqualTo(50);
	}

	@Test
	@DisplayName("confirm 메서드 성능 비교")
	void compareConfirmMethod() throws InterruptedException {
		// given
		User user = userRepository.save(User.builder()
			.customerPaymentKey("customerPaymentKey")
			.email("c@naver.com")
			.name("홍길동")
			.phone("01012345678")
			.role(RoleType.ROLE_USER)
			.username("username")
			.build());

		Mockito.when(securityUtil.getCurrentUser()).thenReturn(user);

		Product product = new Product();
		product.update(1000, 100, "상품1", "설명");
		product = productRepository.save(product);

		OrderCreateRequestDTO dto = new OrderCreateRequestDTO();
		dto.setName("홍길동");
		dto.setPhone("01012345678");
		dto.setAddress("서울");
		dto.setAddressDetail("어딘가");
		dto.setOrderType(OrderType.BUY_NOW);
		dto.setProductId(product.getId());
		dto.setQuantity(1);

		int concurrency = 100;
		int poolSize = 32;

		// 서로 다른 주문/결제 요청 준비 (두 번 돌릴 거라 2배)
		List<PayConfirmDTO> reqs = new ArrayList<>();
		for (int i = 0; i < concurrency * 2; i++) {
			Orders orders = orderService.prepareOrderFromBuyNow(dto);
			reqs.add(new PayConfirmDTO(
				"test_payment_key_" + i,
				orders.getOrderNumber(),
				orders.getFinalPrice()
			));
		}
		List<PayConfirmDTO> reqs2 = new ArrayList<>();
		for (int i = 0; i < concurrency * 2; i++) {
			Orders orders = orderService.prepareOrderFromBuyNow(dto);
			reqs2.add(new PayConfirmDTO(
				"test2_payment_key_" + i,
				orders.getOrderNumber(),
				orders.getFinalPrice()
			));
		}

		// when + then
		runBenchmark(
			"original1",
			poolSize,
			concurrency,
			reqs.subList(concurrency, concurrency * 2),
			(PayConfirmDTO r) -> payService.confirm_before(r, user.getId())
		);

		runBenchmark(
			"improved1",
			poolSize,
			concurrency,
			reqs.subList(0, concurrency),
			(PayConfirmDTO r) -> payService.confirm(r, user.getId())
		);

		runBenchmark(
			"original2",
			poolSize,
			concurrency,
			reqs2.subList(concurrency, concurrency * 2),
			(PayConfirmDTO r) -> payService.confirm_before(r, user.getId())
		);

		runBenchmark(
			"improved2",
			poolSize,
			concurrency,
			reqs2.subList(0, concurrency),
			(PayConfirmDTO r) -> payService.confirm(r, user.getId())
		);
	}

	@FunctionalInterface
	private interface PayCaller {
		void call(PayConfirmDTO req) throws Exception;
	}

	private void runBenchmark(
		String version,
		int poolSize,
		int n,
		List<PayConfirmDTO> reqs,
		PayCaller caller
	) throws InterruptedException {

		// (옵션) 간단 워밍업
		// warmup.run();

		List<Long> times = Collections.synchronizedList(new ArrayList<>());
		AtomicInteger fail = new AtomicInteger();

		ExecutorService pool = Executors.newFixedThreadPool(poolSize);
		CountDownLatch done = new CountDownLatch(n);

		long startTime = System.currentTimeMillis();

		for (int i = 0; i < n; i++) {
			PayConfirmDTO req = reqs.get(i);

			pool.submit(() -> {
				long start = System.currentTimeMillis();
				try {
					caller.call(req);
					times.add(System.currentTimeMillis() - start);
				} catch (Exception e) {
					fail.incrementAndGet();
					log.error("{} pay confirm 실패", version, e);
				} finally {
					done.countDown();
				}
			});
		}

		done.await(60, TimeUnit.SECONDS);
		pool.shutdown();
		pool.awaitTermination(10, TimeUnit.SECONDS);

		logTestResults(version, startTime, times, n, poolSize, fail.get());
	}

	private void logTestResults(
		String version,
		long startTime,
		List<Long> executionTimes,
		int n,
		int poolSize,
		int failCount
	) {
		long totalExecutionTime = System.currentTimeMillis() - startTime;

		log.info("=== {} 성능 테스트 결과 ===", version);
		log.info("동시 요청 수: {}", n);
		log.info("커넥션 풀 사이즈: {}", poolSize);
		log.info("실패 수: {}", failCount);
		log.info("총 실행 시간: {}ms", totalExecutionTime);

		if (executionTimes.isEmpty()) {
			log.info("성공한 요청이 없어서 상세 지표(평균/최소/최대)를 계산할 수 없음");
			return;
		}

		double avg = executionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
		long min = executionTimes.stream().mapToLong(Long::longValue).min().orElse(0);
		long max = executionTimes.stream().mapToLong(Long::longValue).max().orElse(0);

		log.info("평균 실행 시간: {}ms", String.format("%.2f", avg));
		log.info("최소 실행 시간: {}ms", min);
		log.info("최대 실행 시간: {}ms", max);
	}

}