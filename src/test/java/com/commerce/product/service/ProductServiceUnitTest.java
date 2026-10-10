package com.commerce.product.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.exception.ApiException;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.repository.ProductOptionRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceUnitTest {

	@InjectMocks
	private ProductService productService;

	@Mock
	private ProductOptionRepository productOptionRepository;

	private static final Long PRODUCT_ID = 1L;
	private static final Long OTHER_PRODUCT_ID = 2L;
	private static final Long OPTION_ID = 10L;

	@Nested
	@DisplayName("resolveOption - 장바구니·주문에 쓸 옵션 찾기")
	class ResolveOption {

		@Test
		@DisplayName("해당 상품의 옵션이면 그 옵션을 반환한다")
		void returnsOptionOfProduct() {
			// given
			ProductOption option = createOption(OPTION_ID, PRODUCT_ID);
			given(productOptionRepository.findById(OPTION_ID)).willReturn(Optional.of(option));

			// when
			ProductOption result = productService.resolveOption(PRODUCT_ID, OPTION_ID);

			// then
			assertThat(result).isSameAs(option);
		}

		@Test
		@DisplayName("옵션을 고르지 않으면 PRODUCT_OPTION_REQUIRED 예외가 발생한다")
		void optionRequired() {
			assertThatThrownBy(() -> productService.resolveOption(PRODUCT_ID, null))
				.isInstanceOf(ApiException.class)
				.extracting("responseCode").isEqualTo(GeneralResponseCode.PRODUCT_OPTION_REQUIRED);
			verifyNoInteractions(productOptionRepository);
		}

		@Test
		@DisplayName("없는 옵션이면 PRODUCT_OPTION_NOT_FOUND 예외가 발생한다")
		void optionNotFound() {
			// given
			given(productOptionRepository.findById(OPTION_ID)).willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> productService.resolveOption(PRODUCT_ID, OPTION_ID))
				.isInstanceOf(ApiException.class)
				.extracting("responseCode").isEqualTo(GeneralResponseCode.PRODUCT_OPTION_NOT_FOUND);
		}

		@Test
		@DisplayName("다른 상품의 옵션이면 PRODUCT_OPTION_MISMATCH 예외가 발생한다")
		void optionOfOtherProduct() {
			// given
			ProductOption otherProductOption = createOption(OPTION_ID, OTHER_PRODUCT_ID);
			given(productOptionRepository.findById(OPTION_ID)).willReturn(Optional.of(otherProductOption));

			// when & then
			assertThatThrownBy(() -> productService.resolveOption(PRODUCT_ID, OPTION_ID))
				.isInstanceOf(ApiException.class)
				.extracting("responseCode").isEqualTo(GeneralResponseCode.PRODUCT_OPTION_MISMATCH);
		}
	}

	private ProductOption createOption(Long optionId, Long productId) {
		Product product = new Product();
		ReflectionTestUtils.setField(product, "id", productId);
		ProductOption option = new ProductOption("옵션" + optionId, 50, 0);
		product.addOption(option);
		ReflectionTestUtils.setField(option, "id", optionId);
		return option;
	}
}
