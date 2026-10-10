package com.commerce.product.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.exception.ApiException;
import com.commerce.common.storage.FileStorage;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.repository.ProductOptionRepository;
import com.commerce.product.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class ProductServiceUnitTest {

	@InjectMocks
	private ProductService productService;

	@Mock
	private ProductOptionRepository productOptionRepository;
	@Mock
	private ProductRepository productRepository;
	@Mock
	private FileStorage fileStorage;

	private static final Long PRODUCT_ID = 1L;
	private static final Long OTHER_PRODUCT_ID = 2L;
	private static final Long OPTION_ID = 10L;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Nested
	@DisplayName("resolveOption - 장바구니·주문에 쓸 옵션 찾기")
	class ResolveOption {

		@Test
		@DisplayName("해당 상품의 옵션이면 그 옵션을 반환한다")
		void returnsOptionOfProduct() {
			// given
			ProductOption option = createOption(OPTION_ID, createProduct(PRODUCT_ID));
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
		@DisplayName("삭제된 옵션이면 PRODUCT_OPTION_NOT_FOUND 예외가 발생한다")
		void deletedOption() {
			// given
			ProductOption option = createOption(OPTION_ID, createProduct(PRODUCT_ID));
			option.softDelete("admin");
			given(productOptionRepository.findById(OPTION_ID)).willReturn(Optional.of(option));

			// when & then
			assertThatThrownBy(() -> productService.resolveOption(PRODUCT_ID, OPTION_ID))
				.isInstanceOf(ApiException.class)
				.extracting("responseCode").isEqualTo(GeneralResponseCode.PRODUCT_OPTION_NOT_FOUND);
		}

		@Test
		@DisplayName("다른 상품의 옵션이면 PRODUCT_OPTION_MISMATCH 예외가 발생한다")
		void optionOfOtherProduct() {
			// given
			ProductOption otherProductOption = createOption(OPTION_ID, createProduct(OTHER_PRODUCT_ID));
			given(productOptionRepository.findById(OPTION_ID)).willReturn(Optional.of(otherProductOption));

			// when & then
			assertThatThrownBy(() -> productService.resolveOption(PRODUCT_ID, OPTION_ID))
				.isInstanceOf(ApiException.class)
				.extracting("responseCode").isEqualTo(GeneralResponseCode.PRODUCT_OPTION_MISMATCH);
		}
	}

	@Nested
	@DisplayName("deleteProduct - 상품 삭제")
	class DeleteProduct {

		@Test
		@DisplayName("상품과 옵션을 soft delete하고 이미지 파일은 지우지 않는다")
		void softDeletesProductAndOptions() {
			// given
			SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
				User.withUsername("admin").password("pw").roles("ADMIN").build(), null, "ROLE_ADMIN"));
			Product product = createProduct(PRODUCT_ID);
			ProductOption m = createOption(10L, product);
			ProductOption l = createOption(11L, product);
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			// when
			productService.deleteProduct(PRODUCT_ID);

			// then
			assertThat(product.isDeleted()).isTrue();
			assertThat(product.getDeletedBy()).isEqualTo("admin");
			assertThat(m.isDeleted()).isTrue();
			assertThat(l.isDeleted()).isTrue();
			verify(productRepository, never()).delete(any());
			verifyNoInteractions(fileStorage);
		}

		@Test
		@DisplayName("이미 삭제된 상품이면 PRODUCT_NOT_FOUND 예외가 발생한다")
		void alreadyDeleted() {
			// given
			Product product = createProduct(PRODUCT_ID);
			product.softDelete("admin");
			given(productRepository.findById(PRODUCT_ID)).willReturn(Optional.of(product));

			// when & then
			assertThatThrownBy(() -> productService.deleteProduct(PRODUCT_ID))
				.isInstanceOf(ApiException.class)
				.extracting("responseCode").isEqualTo(GeneralResponseCode.PRODUCT_NOT_FOUND);
		}
	}

	private Product createProduct(Long productId) {
		Product product = new Product();
		ReflectionTestUtils.setField(product, "id", productId);
		return product;
	}

	private ProductOption createOption(Long optionId, Product product) {
		ProductOption option = new ProductOption("옵션" + optionId, 50, 0);
		product.addOption(option);
		ReflectionTestUtils.setField(option, "id", optionId);
		return option;
	}
}
