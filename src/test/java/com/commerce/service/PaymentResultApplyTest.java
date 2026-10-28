package com.commerce.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.commerce.cart.domain.CartProduct;
import com.commerce.cart.repository.CartProductRepository;
import com.commerce.cart.repository.CartRepository;
import com.commerce.cart.service.CartService;
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
import com.commerce.payment.service.PaymentTxService;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.repository.ProductOptionRepository;
import com.commerce.product.repository.ProductRepository;
import com.commerce.user.domain.User;
import com.commerce.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 결제 결과 반영(성공 처리·실패 처리) 테스트
 * 요청 처리 중 확정과 보정 스케줄러가 같은 주문에 여러 번·동시에 반영해도 상태는 한 번만 바뀌어야 한다.
 */
@IntegrationTest
class PaymentResultApplyTest {

	private static final int INITIAL_STOCK = 10;

	@Autowired PaymentTxService paymentTxService;
	@Autowired OrderService orderService;
	@Autowired OrderRepository orderRepository;
	@Autowired OrderProductRepository orderProductRepository;
	@Autowired ProductRepository productRepository;
	@Autowired ProductOptionRepository productOptionRepository;
	@Autowired UserRepository userRepository;
	@Autowired CartRepository cartRepository;
	@Autowired CartProductRepository cartProductRepository;
	@Autowired CartService cartService;
	@Autowired ObjectMapper objectMapper;
	@Autowired JdbcTemplate jdbcTemplate;

	@MockitoBean SecurityUtil securityUtil;

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

		Product product = new Product();
		product.update(1000, "상품1", "설명");
		ProductOption option = new ProductOption("단품", INITIAL_STOCK, 0);
		product.addOption(option);
		productId = productRepository.save(product).getId();
		optionId = option.getId();
	}

	@AfterEach
	void cleanUp() {
		jdbcTemplate.update("delete from order_cart_product");
		orderProductRepository.deleteAllInBatch();
		orderRepository.deleteAllInBatch();
		cartProductRepository.deleteAllInBatch();
		cartRepository.deleteAllInBatch();
		productOptionRepository.deleteAllInBatch();
		productRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("이미 취소된 주문에 결제 성공이 반영되면, 예외 없이 취소 상태를 유지한다")
	void applySuccess_canceledOrder_ignored() throws Exception {
		// given - 결제 진행 중 → 토스 실패로 취소(재고 복원)
		Orders order = pendingOrder();
		paymentTxService.restoreStockOnTossFailure(order.getOrderNumber());

		// when
		paymentTxService.applyPaymentSuccess(order.getOrderNumber(), tossDone(), "paymentKey");

		// then
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.CANCELED);
		assertThat(after.getApprovedAt()).as("결제 성공 정보가 기록되지 않음").isNull();
		assertThat(stock()).isEqualTo(INITIAL_STOCK);
	}

	@Test
	@DisplayName("이미 결제 완료된 주문에 결제 실패가 반영되면, 재고를 복원하지 않고 결제 완료 상태를 유지한다")
	void restoreStock_paidOrder_ignored() throws Exception {
		// given - 결제 진행 중 → 결제 성공(재고 차감 유지)
		Orders order = pendingOrder();
		paymentTxService.applyPaymentSuccess(order.getOrderNumber(), tossDone(), "paymentKey");

		// when
		paymentTxService.restoreStockOnTossFailure(order.getOrderNumber());

		// then
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.PAID);
		assertThat(stock()).isEqualTo(INITIAL_STOCK - 1);
	}

	@Test
	@DisplayName("로그인 정보가 없어도(보정 스케줄러) 장바구니 주문의 결제 성공을 반영하면, 주문자의 장바구니 상품을 삭제한다")
	void applySuccess_withoutLogin_deletesOrdererCart() throws Exception {
		// given - 장바구니에 담고 장바구니 주문 → 결제 진행 중
		cartService.addCart(productId, optionId, 1);
		Long cartProductId = cartProductRepository.findAll().get(0).getId();

		OrderCreateRequestDTO dto = orderForm(OrderType.CART);
		dto.setCartProductIds(List.of(cartProductId));
		Orders order = orderService.prepareOrderFromCart(dto);
		paymentTxService.lockAndDeductStock(order.getOrderNumber(), "paymentKey");

		SecurityContextHolder.clearContext();

		// when
		paymentTxService.applyPaymentSuccess(order.getOrderNumber(), tossDone(), "paymentKey");

		// then
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).isEqualTo(OrderStatus.PAID);

		CartProduct cartProduct = cartProductRepository.findById(cartProductId).orElseThrow();
		assertThat(cartProduct.isDeleted()).isTrue();
		assertThat(cartProduct.getDeletedBy()).isEqualTo("username");
	}

	@Test
	@DisplayName("결제 성공 반영 중에 결제 실패가 겹쳐 들어오면, 먼저 시작한 성공만 반영되고 재고는 복원되지 않는다")
	void applySuccessAndRestoreStock_concurrent_onlyFirstApplied() throws Exception {
		// given
		Orders order = pendingOrder();

		// 성공 반영이 주문 상태를 확인한 뒤(토스 응답의 결제 수단을 읽는 시점) 멈추게 한다
		CountDownLatch successPaused = new CountDownLatch(1);
		CountDownLatch resumeSuccess = new CountDownLatch(1);
		JsonNode tossResponse = spy(tossDone());
		doAnswer(invocation -> {
			successPaused.countDown();
			resumeSuccess.await(5, TimeUnit.SECONDS);
			return invocation.callRealMethod();
		}).when(tossResponse).path("method");

		// when
		ExecutorService pool = Executors.newFixedThreadPool(2);
		Future<?> success = pool.submit(() ->
			paymentTxService.applyPaymentSuccess(order.getOrderNumber(), tossResponse, "paymentKey"));
		assertThat(successPaused.await(5, TimeUnit.SECONDS)).isTrue();

		Future<?> failure = pool.submit(() -> paymentTxService.restoreStockOnTossFailure(order.getOrderNumber()));
		// - 락이 없으면: 실패 반영이 결제 진행 중 상태를 보고 바로 재고 복원·취소까지 끝낸다
		// - 락이 있으면: 성공 반영이 끝날 때까지 주문 행 락에서 기다린다
		Thread.sleep(1000);
		resumeSuccess.countDown();

		success.get(10, TimeUnit.SECONDS);
		failure.get(10, TimeUnit.SECONDS);
		pool.shutdown();

		// then
		Orders after = orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow();
		assertThat(after.getOrderStatus()).as("주문 상태").isEqualTo(OrderStatus.PAID);
		assertThat(stock()).as("재고").isEqualTo(INITIAL_STOCK - 1);
	}

	// 바로구매 주문 생성 → 결제 확정 시작(PAYMENT_PENDING, 재고 차감)
	private Orders pendingOrder() {
		Orders order = orderService.prepareOrderFromBuyNow(orderForm(OrderType.BUY_NOW));
		paymentTxService.lockAndDeductStock(order.getOrderNumber(), "paymentKey");
		return order;
	}

	private OrderCreateRequestDTO orderForm(OrderType orderType) {
		OrderCreateRequestDTO dto = new OrderCreateRequestDTO();
		dto.setName("홍길동");
		dto.setPhone("01012345678");
		dto.setAddress("서울");
		dto.setAddressDetail("어딘가");
		dto.setOrderType(orderType);
		dto.setProductId(productId);
		dto.setOptionId(optionId);
		dto.setQuantity(1);
		return dto;
	}

	private JsonNode tossDone() throws Exception {
		return objectMapper.readTree("""
			{ "status": "DONE", "method": "카드", "approvedAt": "2026-01-03T01:23:45+09:00" }
			""");
	}

	private int stock() {
		return productOptionRepository.findById(optionId).orElseThrow().getStock();
	}
}
