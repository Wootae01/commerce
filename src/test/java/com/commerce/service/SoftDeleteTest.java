package com.commerce.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.commerce.cart.domain.Cart;
import com.commerce.cart.domain.CartProduct;
import com.commerce.cart.dto.CartProductDTO;
import com.commerce.cart.repository.CartProductRepository;
import com.commerce.cart.repository.CartRepository;
import com.commerce.common.enums.RoleType;
import com.commerce.common.storage.UploadFile;
import com.commerce.config.IntegrationTest;
import com.commerce.product.domain.Image;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.dto.ProductHomeDTO;
import com.commerce.product.dto.ProductMainImageRow;
import com.commerce.product.repository.ProductOptionRepository;
import com.commerce.product.repository.ProductRepository;
import com.commerce.user.domain.User;
import com.commerce.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

@IntegrationTest
@Transactional
class SoftDeleteTest {

	@Autowired
	private EntityManager em;
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private ProductOptionRepository productOptionRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CartRepository cartRepository;
	@Autowired
	private CartProductRepository cartProductRepository;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("로그인 상태로 저장하면 createdBy, updatedBy에 username이 채워진다")
	void auditUsername() {
		// given
		login("admin");

		// when
		Product product = saveProduct("감사 상품");
		em.flush();
		em.clear();

		// then
		Product found = productRepository.findById(product.getId()).orElseThrow();
		assertThat(found.getCreatedBy()).isEqualTo("admin");
		assertThat(found.getUpdatedBy()).isEqualTo("admin");
	}

	@Test
	@DisplayName("로그인 전에 저장하면 createdBy는 비어 있다")
	void auditWithoutLogin() {
		// when
		Product product = saveProduct("비로그인 상품");
		em.flush();
		em.clear();

		// then
		assertThat(productRepository.findById(product.getId()).orElseThrow().getCreatedBy()).isNull();
	}

	@Test
	@DisplayName("삭제된 상품은 목록·검색·상세에서 빠지고, 주문 내역용 이미지 조회에는 남는다")
	void deletedProductHiddenExceptOrderHistory() {
		// given
		Product active = saveProduct("판매중 상품");
		Product deleted = saveProduct("삭제 상품");
		deleted.softDelete("admin");
		em.flush();
		em.clear();

		// when
		List<Long> homeIds = productRepository.findHomeProducts(PageRequest.of(0, 100)).getContent().stream()
			.map(ProductHomeDTO::getId).toList();
		List<Long> searchIds = productRepository.searchProducts("상품", null, null, PageRequest.of(0, 100))
			.getContent().stream().map(ProductHomeDTO::getId).toList();
		List<ProductMainImageRow> mainImages = productRepository.findMainImages(List.of(deleted.getId()));

		// then
		assertThat(homeIds).contains(active.getId()).doesNotContain(deleted.getId());
		assertThat(searchIds).contains(active.getId()).doesNotContain(deleted.getId());
		assertThat(productRepository.findByIdWithImage(deleted.getId())).isEmpty();
		assertThat(mainImages).hasSize(1);
	}

	@Test
	@DisplayName("삭제된 옵션은 상품 상세 옵션과 관리자 목록 재고 합계에서 빠진다")
	void deletedOptionHidden() {
		// given
		Product product = saveProduct("옵션 상품");
		ProductOption l = new ProductOption("L", 5, 1000);
		product.addOption(l);
		em.flush();
		l.softDelete("admin");
		em.flush();
		em.clear();

		// when
		List<ProductOption> options = productOptionRepository.findByProductId(product.getId());
		long adminStock = productRepository.findAdminProductListDTO(PageRequest.of(0, 100)).getContent().stream()
			.filter(dto -> dto.getId().equals(product.getId()))
			.findFirst().orElseThrow()
			.getStock();

		// then - 단품(10)만 남는다
		assertThat(options).extracting(ProductOption::getName).containsExactly("단품");
		assertThat(adminStock).isEqualTo(10);
	}

	@Test
	@DisplayName("결제 후 장바구니 정리는 soft delete이고, 장바구니 목록에서 빠진다")
	void cartCleanupIsSoftDelete() {
		// given
		User user = userRepository.save(User.builder()
			.username("soft-delete-user")
			.role(RoleType.ROLE_USER)
			.name("홍길동")
			.build());
		Product product = saveProduct("장바구니 상품");
		Cart cart = new Cart(user);
		CartProduct cartProduct = new CartProduct(cart, product, product.getOptions().get(0), 1, true);
		cart.addProduct(cartProduct);
		cartRepository.save(cart);
		em.flush();

		// when
		int updated = cartProductRepository.deleteSelectedFromUserCart(List.of(cartProduct.getId()), user.getId(), "user");
		em.clear();

		// then
		assertThat(updated).isEqualTo(1);
		assertThat(cartProductRepository.findById(cartProduct.getId())).isPresent()
			.get().extracting(CartProduct::isDeleted).isEqualTo(true);
		assertThat(cartProductRepository.findCartRows(cart.getId())).extracting(CartProductDTO::getId)
			.doesNotContain(cartProduct.getId());
	}

	private Product saveProduct(String name) {
		Product product = new Product();
		product.update(1000, name, "설명");
		product.addOption(new ProductOption("단품", 10, 0));
		product.setMainImage(Image.createMainImage(new UploadFile("", "/images/default.png")));
		return productRepository.save(product);
	}

	private void login(String username) {
		SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
			org.springframework.security.core.userdetails.User.withUsername(username).password("pw").roles("ADMIN").build(),
			null, "ROLE_ADMIN"));
	}
}
