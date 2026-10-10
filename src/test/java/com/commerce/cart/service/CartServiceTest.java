package com.commerce.cart.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.commerce.cart.domain.Cart;
import com.commerce.cart.domain.CartProduct;
import com.commerce.cart.repository.CartProductRepository;
import com.commerce.cart.repository.CartRepository;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.enums.RoleType;
import com.commerce.common.exception.ApiException;
import com.commerce.common.util.ProductImageUtil;
import com.commerce.common.util.SecurityUtil;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.service.ProductService;
import com.commerce.user.domain.User;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

	@InjectMocks
	private CartService cartService;

	@Mock
	private CartRepository cartRepository;
	@Mock
	private CartProductRepository cartProductRepository;
	@Mock
	private ProductService productService;
	@Mock
	private SecurityUtil securityUtil;
	@Mock
	private ProductImageUtil productImageUtil;

	private static final Long PRODUCT_ID = 1L;
	private static final Long OPTION_ID = 10L;
	private static final Long OTHER_OPTION_ID = 11L;
	private static final Long CART_ID = 100L;

	private User user;
	private Product product;
	private ProductOption option;
	private Cart cart;

	@BeforeEach
	void setUp() {
		user = User.builder()
			.username("user")
			.role(RoleType.ROLE_USER)
			.name("홍길동")
			.build();
		ReflectionTestUtils.setField(user, "id", 1L);

		product = createProduct(PRODUCT_ID);
		option = createOption(OPTION_ID, product, 1000);

		cart = new Cart(user);
		ReflectionTestUtils.setField(cart, "id", CART_ID);
	}

	@Nested
	@DisplayName("addCart - 장바구니 담기")
	class AddCart {

		@Test
		@DisplayName("처음 담는 옵션이면 요청한 수량으로 새 항목을 추가한다")
		void addNewOption() {
			// given
			givenProductAndOption(option);
			given(securityUtil.getCurrentUser()).willReturn(user);
			given(cartRepository.findByUser(user)).willReturn(Optional.of(cart));
			given(cartProductRepository.findByCartIdWithProductAndOption(CART_ID)).willReturn(List.of());

			// when
			cartService.addCart(PRODUCT_ID, OPTION_ID, 3);

			// then
			assertThat(cart.getCartProducts()).hasSize(1);
			CartProduct added = cart.getCartProducts().get(0);
			assertThat(added.getProduct()).isSameAs(product);
			assertThat(added.getProductOption()).isSameAs(option);
			assertThat(added.getQuantity()).isEqualTo(3);
			assertThat(added.isChecked()).isFalse();
			verify(cartRepository).save(cart);
		}

		@Test
		@DisplayName("이미 담긴 같은 옵션을 다시 담으면 요청한 수량만큼 더한다")
		void addSameOptionIncreasesQuantity() {
			// given
			CartProduct existing = new CartProduct(cart, product, option, 2, true);
			givenProductAndOption(option);
			given(securityUtil.getCurrentUser()).willReturn(user);
			given(cartRepository.findByUser(user)).willReturn(Optional.of(cart));
			given(cartProductRepository.findByCartIdWithProductAndOption(CART_ID)).willReturn(List.of(existing));

			// when
			cartService.addCart(PRODUCT_ID, OPTION_ID, 3);

			// then
			assertThat(existing.getQuantity()).isEqualTo(5);
			verify(cartProductRepository).save(existing);
			verify(cartRepository, never()).save(any());
		}

		@Test
		@DisplayName("같은 상품이라도 옵션이 다르면 새 항목으로 추가한다")
		void addSameProductWithDifferentOption() {
			// given
			ProductOption otherOption = createOption(OTHER_OPTION_ID, product, 2000);
			CartProduct existing = new CartProduct(cart, product, option, 1, false);
			givenProductAndOption(otherOption);
			given(securityUtil.getCurrentUser()).willReturn(user);
			given(cartRepository.findByUser(user)).willReturn(Optional.of(cart));
			given(cartProductRepository.findByCartIdWithProductAndOption(CART_ID)).willReturn(List.of(existing));

			// when
			cartService.addCart(PRODUCT_ID, OTHER_OPTION_ID, 2);

			// then
			assertThat(existing.getQuantity()).isEqualTo(1);
			assertThat(cart.getCartProducts()).hasSize(1);
			CartProduct added = cart.getCartProducts().get(0);
			assertThat(added.getProductOption()).isSameAs(otherOption);
			assertThat(added.getQuantity()).isEqualTo(2);
			verify(cartRepository).save(cart);
		}

		@Test
		@DisplayName("장바구니가 없는 사용자면 장바구니를 새로 만들어 담는다")
		void createCartWhenUserHasNone() {
			// given
			givenProductAndOption(option);
			given(securityUtil.getCurrentUser()).willReturn(user);
			given(cartRepository.findByUser(user)).willReturn(Optional.empty());

			// when
			cartService.addCart(PRODUCT_ID, OPTION_ID, 1);

			// then
			ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
			verify(cartRepository).save(captor.capture());
			Cart saved = captor.getValue();
			assertThat(saved.getUser()).isSameAs(user);
			assertThat(saved.getCartProducts()).hasSize(1);
			assertThat(saved.getCartProducts().get(0).getQuantity()).isEqualTo(1);
		}

		@Test
		@DisplayName("없는 상품이면 PRODUCT_NOT_FOUND 예외가 발생한다")
		void productNotFound() {
			// given
			given(productService.findById(PRODUCT_ID))
				.willThrow(new ApiException(GeneralResponseCode.PRODUCT_NOT_FOUND));

			// when & then
			assertThatThrownBy(() -> cartService.addCart(PRODUCT_ID, OPTION_ID, 1))
				.isInstanceOf(ApiException.class)
				.extracting("responseCode").isEqualTo(GeneralResponseCode.PRODUCT_NOT_FOUND);
			verifyNoInteractions(cartRepository, cartProductRepository);
		}

		@Test
		@DisplayName("옵션을 찾지 못하면 예외가 그대로 전달되고 장바구니는 건드리지 않는다")
		void optionResolveFails() {
			// given
			given(productService.findById(PRODUCT_ID)).willReturn(product);
			given(productService.resolveOption(PRODUCT_ID, null))
				.willThrow(new ApiException(GeneralResponseCode.PRODUCT_OPTION_REQUIRED));

			// when & then
			assertThatThrownBy(() -> cartService.addCart(PRODUCT_ID, null, 1))
				.isInstanceOf(ApiException.class)
				.extracting("responseCode").isEqualTo(GeneralResponseCode.PRODUCT_OPTION_REQUIRED);
			verifyNoInteractions(cartRepository, cartProductRepository);
		}
	}

	@Nested
	@DisplayName("getTotalPrice - 선택한 상품 합계")
	class GetTotalPrice {

		@Test
		@DisplayName("선택한 항목만 (상품 가격 + 옵션 추가 금액) x 수량으로 더한다")
		void sumCheckedItemsWithAdditionalPrice() {
			// given
			ProductOption otherOption = createOption(OTHER_OPTION_ID, product, 2000);
			CartProduct checked1 = new CartProduct(cart, product, option, 2, true);       // (10000 + 1000) * 2
			CartProduct checked2 = new CartProduct(cart, product, otherOption, 1, true);  // (10000 + 2000) * 1
			CartProduct unchecked = new CartProduct(cart, product, option, 5, false);

			// when
			int total = cartService.getTotalPrice(List.of(checked1, checked2, unchecked));

			// then
			assertThat(total).isEqualTo(22000 + 12000);
		}
	}

	private void givenProductAndOption(ProductOption resolved) {
		given(productService.findById(PRODUCT_ID)).willReturn(product);
		given(productService.resolveOption(PRODUCT_ID, resolved.getId())).willReturn(resolved);
	}

	private Product createProduct(Long id) {
		Product product = new Product();
		product.update(10000, "상품" + id, "설명");
		ReflectionTestUtils.setField(product, "id", id);
		return product;
	}

	private ProductOption createOption(Long id, Product product, int additionalPrice) {
		ProductOption option = new ProductOption("옵션" + id, 50, additionalPrice);
		product.addOption(option);
		ReflectionTestUtils.setField(option, "id", id);
		return option;
	}
}
