package com.commerce.service;

import static org.mockito.Mockito.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * 결제 확정·보정 통합 테스트의 공통 설정과 준비 코드.
 * 설정(어노테이션, Mock 목록)이 같아야 스프링 컨텍스트를 같이 쓰므로, Mock을 더할 때는 여기에 더한다.
 * 토스는 WireMock으로 대신하고, 재고 1개짜리 바로구매 주문을 만든다.
 */
@IntegrationTest
@EnableWireMock
@TestPropertySource(properties = {
	"toss.base-url=${wiremock.server.baseUrl}"
})
abstract class PaymentIntegrationTestSupport {

	static final int INITIAL_STOCK = 10;

	@Autowired PayService payService;
	@Autowired PaymentRecoveryScheduler paymentRecoveryScheduler;
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

	Long userId;
	Long productId;
	Long optionId;

	@BeforeEach
	void setUpUserAndProduct() {
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

	Orders prepareOrder() {
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

	int stock() {
		return productOptionRepository.findById(optionId).orElseThrow().getStock();
	}

	OrderStatus statusOf(Orders order) {
		return orderRepository.findByOrderNumber(order.getOrderNumber()).orElseThrow().getOrderStatus();
	}

	// 보정 스케줄러의 시간 조건(PENDING이 된 지 1분)을 만든다
	void markUpdatedAt(Orders order, LocalDateTime updatedAt) {
		jdbcTemplate.update("update orders set updated_at = ? where order_number = ?",
			updatedAt, order.getOrderNumber());
	}
}
