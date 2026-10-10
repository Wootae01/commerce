package com.commerce.payment.service;

import com.commerce.order.domain.OrderProduct;
import com.commerce.order.repository.OrderProductRepository;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.repository.ProductJdbcRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentTxServiceTest {

	@InjectMocks
	private PaymentTxService paymentTxService;

	@Mock
	private OrderProductRepository orderProductRepository;
	@Mock
	private ProductJdbcRepository productJdbcRepository;

	private static final Long ORDER_ID = 1L;

	@Nested
	@DisplayName("updateStock - 주문 상품 재고 수정")
	class UpdateStock {

		@Test
		@DisplayName("같은 상품의 다른 옵션은 옵션별로 재고를 수정한다")
		void updatesStockPerOption() {
			// given - 한 상품의 옵션 M 2개, L 1개
			Product product = createProduct(1L);
			ProductOption m = createOption(10L, product);
			ProductOption l = createOption(11L, product);
			given(orderProductRepository.findOrderProductByOrderIdWithProduct(ORDER_ID)).willReturn(List.of(
				createOrderProduct(product, m, 2),
				createOrderProduct(product, l, 1)
			));

			// when
			paymentTxService.updateStock(ORDER_ID, false);

			// then - 상품 단위로 합치지 않고 옵션마다 차감한다
			verify(productJdbcRepository).updateOptionStock(Map.of(10L, 2, 11L, 1), false);
		}

		@Test
		@DisplayName("같은 옵션이 여러 줄이면 수량을 합쳐 한 번에 수정한다")
		void sumsQuantityOfSameOption() {
			// given
			Product product = createProduct(1L);
			ProductOption m = createOption(10L, product);
			given(orderProductRepository.findOrderProductByOrderIdWithProduct(ORDER_ID)).willReturn(List.of(
				createOrderProduct(product, m, 2),
				createOrderProduct(product, m, 3)
			));

			// when
			paymentTxService.updateStock(ORDER_ID, true);

			// then
			verify(productJdbcRepository).updateOptionStock(Map.of(10L, 5), true);
		}
	}

	private Product createProduct(Long id) {
		Product product = new Product();
		ReflectionTestUtils.setField(product, "id", id);
		return product;
	}

	private ProductOption createOption(Long id, Product product) {
		ProductOption option = new ProductOption("옵션" + id, 50, 0);
		product.addOption(option);
		ReflectionTestUtils.setField(option, "id", id);
		return option;
	}

	private OrderProduct createOrderProduct(Product product, ProductOption option, int quantity) {
		return OrderProduct.builder()
			.product(product)
			.productOption(option)
			.quantity(quantity)
			.price(product.getPrice() + option.getAdditionalPrice())
			.build();
	}
}
