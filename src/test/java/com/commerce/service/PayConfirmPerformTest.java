package com.commerce.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
public class PayConfirmPerformTest {
	private static final int BACKGROUND_THREAD_COUNT = 28; // 커넥션 풀 점유용
	private static final int CONCURRENCY = 100;
	private static final int POOL_SIZE = 32;

	@Autowired
	private PayService payService;
	@Autowired private OrderService orderService;
	@Autowired private ProductRepository productRepository;
	@Autowired private UserRepository userRepository;

	@Autowired private PlatformTransactionManager transactionManager;

	@MockitoBean
	SecurityUtil securityUtil;

	private ExecutorService backgroundExecutor;

	@BeforeEach
	void setUp() {
		backgroundExecutor = Executors.newFixedThreadPool(BACKGROUND_THREAD_COUNT);
	}

	@AfterEach
	void tearDown() {
		if (backgroundExecutor != null) {
			backgroundExecutor.shutdownNow();
		}
	}

	@Test
	@DisplayName("confirm 메서드 성능 비교 - 커넥션 풀 점유 상황")
	void compareConfirmMethod() throws InterruptedException {

		// 1) given - 테스트 데이터 먼저 준비 (점유 전에 끝내야 setup이 안 막힘)
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

		// 서로 다른 주문/결제 요청 준비(원본/개선 각각 2번씩 돌릴 거라 2배)
		List<PayConfirmDTO> reqs = new ArrayList<>();
		for (int i = 0; i < CONCURRENCY * 2; i++) {
			Orders orders = orderService.prepareOrderFromBuyNow(dto);
			reqs.add(new PayConfirmDTO(
				"pk_" + UUID.randomUUID(),
				orders.getOrderNumber(),
				orders.getFinalPrice()
			));
		}

		// warmUp 용도
		List<PayConfirmDTO> warmUpReqs = new ArrayList<>();
		for (int i = 0; i < CONCURRENCY * 2; i++) {
			Orders orders = orderService.prepareOrderFromBuyNow(dto);
			warmUpReqs.add(new PayConfirmDTO(
				"pk_" + UUID.randomUUID(),
				orders.getOrderNumber(),
				orders.getFinalPrice()
			));
		}


		// 2) when - 커넥션 풀 점유 시작
		occupyConnections();
		Thread.sleep(1000); // 점유가 실제로 걸리도록 잠깐 대기

		// 3) then - 벤치마크
		runBenchmark(
			"warmUpOriginal",
			POOL_SIZE,
			CONCURRENCY,
			warmUpReqs.subList(CONCURRENCY, CONCURRENCY * 2),
			(PayConfirmDTO r) -> payService.confirm_before(r, user.getId())
		);
		runBenchmark(
			"warmUpImproved",
			POOL_SIZE,
			CONCURRENCY,
			warmUpReqs.subList(CONCURRENCY, CONCURRENCY * 2),
			(PayConfirmDTO r) -> payService.confirm(r, user.getId())
		);

		List<Long> originalTimes = runBenchmark(
			"original",
			POOL_SIZE,
			CONCURRENCY,
			reqs.subList(CONCURRENCY, CONCURRENCY * 2),
			(PayConfirmDTO r) -> payService.confirm_before(r, user.getId())
		);

		List<Long> improvedTimes = runBenchmark(
			"improved",
			POOL_SIZE,
			CONCURRENCY,
			reqs.subList(0, CONCURRENCY),
			(PayConfirmDTO r) -> payService.confirm(r, user.getId())
		);

		logTestResults("original", originalTimes, CONCURRENCY, POOL_SIZE);
		logTestResults("improved", improvedTimes, CONCURRENCY, POOL_SIZE);
	}

	@FunctionalInterface
	private interface PayCaller {
		void call(PayConfirmDTO req) throws Exception;
	}

	private List<Long> runBenchmark(
		String version,
		int poolSize,
		int n,
		List<PayConfirmDTO> reqs,
		PayCaller caller
	) throws InterruptedException {

		List<Long> times = Collections.synchronizedList(new ArrayList<>());

		ExecutorService pool = Executors.newFixedThreadPool(poolSize);
		CountDownLatch done = new CountDownLatch(n);


		for (int i = 0; i < n; i++) {
			PayConfirmDTO req = reqs.get(i);

			pool.submit(() -> {
				long start = System.currentTimeMillis();
				try {
					caller.call(req);
					times.add(System.currentTimeMillis() - start);
				} catch (Exception e) {
					log.error("{} pay confirm 실패", version, e);
				} finally {
					done.countDown();
				}
			});
		}

		pool.shutdown();
		pool.awaitTermination(10, TimeUnit.SECONDS);
		done.await(60, TimeUnit.SECONDS);
		return times;
	}

	private void logTestResults(
		String version,
		List<Long> executionTimes,
		int n,
		int poolSize
	) {

		log.info("=== {} 성능 테스트 결과 ===", version);
		log.info("동시 요청 수: {}", n);
		log.info("스레드 풀 사이즈: {}", poolSize);

		if (executionTimes.isEmpty()) {
			log.info("성공한 요청이 없어서 상세 지표 계산 불가");
			return;
		}

		double avg = executionTimes.stream().mapToLong(Long::longValue).average().orElse(0);
		long min = executionTimes.stream().mapToLong(Long::longValue).min().orElse(0);
		long max = executionTimes.stream().mapToLong(Long::longValue).max().orElse(0);

		log.info("평균 실행 시간: {}ms", String.format("%.2f", avg));
		log.info("최소 실행 시간: {}ms", min);
		log.info("최대 실행 시간: {}ms", max);
	}

	// DB 커넥션 풀 점유
	private void occupyConnections() {
		TransactionTemplate tx = new TransactionTemplate(transactionManager);

		for (int i = 0; i < BACKGROUND_THREAD_COUNT; i++) {
			backgroundExecutor.submit(() -> {
				try {
					tx.execute(status -> {
						try {
							Thread.sleep(Integer.MAX_VALUE);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						return null;
					});
				} catch (Exception e) {
					log.error("occupyConnections background error", e);
				}
			});
		}
	}
}
