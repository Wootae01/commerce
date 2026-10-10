package com.commerce.cart.repository;


import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.commerce.cart.domain.CartProduct;
import com.commerce.cart.dto.CartProductDTO;
import com.commerce.order.dto.OrderItemDTO;

/**
 * 장바구니 화면·주문서에는 삭제되지 않은 장바구니 상품 중 상품과 옵션이 판매 중인 것만 보여준다.
 */
public interface CartProductRepository extends JpaRepository<CartProduct, Long> {

	@Query("select cp.id from CartProduct cp where cp.cart.id = :cartId and cp.deletedAt is null")
	List<Long> findIdsByCartId(Long cartId);

	@Modifying
	@Query("""
		update CartProduct cp
		set cp.deletedAt = CURRENT_TIMESTAMP, cp.deletedBy = :deletedBy
		where cp.id in :ids and cp.cart.user.id = :userId and cp.deletedAt is null
		""")
	int deleteSelectedFromUserCart(@Param("ids") List<Long> ids, @Param("userId") Long userId,
		@Param("deletedBy") String deletedBy);

	@Query("""
		select new com.commerce.cart.dto.CartProductDTO (
			cp.id,
			cp.isChecked,
			cp.quantity,
			p.price + po.additionalPrice,
			mi.storeFileName,
			p.name,
			po.name
		)
		from CartProduct cp
		join cp.product p
		left join p.mainImage mi
		join cp.productOption po
		where cp.cart.id = :cartId
		and cp.deletedAt is null and p.deletedAt is null and po.deletedAt is null
		""")
	List<CartProductDTO> findCartRows(Long cartId);

	@Query("""
		select cp from CartProduct cp
		join fetch cp.product p
		left join fetch p.mainImage
		join fetch cp.productOption po
		where cp.id in (:cartProductIds)
		and cp.deletedAt is null and p.deletedAt is null and po.deletedAt is null
		""")
	List<CartProduct> findAllByIdWithProduct(List<Long> cartProductIds);

	// 본인 장바구니에 담긴 상품만 조회
	@Query("select cp from CartProduct cp where cp.id = :id and cp.cart.user.id = :userId and cp.deletedAt is null")
	Optional<CartProduct> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

	// 주문 생성용. 상품·옵션이 삭제되었는지는 주문 쪽에서 검증해 알려준다.
	@Query("""
		select cp from CartProduct cp
		join fetch cp.product p
		left join fetch p.mainImage
		join fetch cp.productOption
		where cp.id in (:cartProductIds) and cp.cart.user.id = :userId
		and cp.deletedAt is null
		""")
	List<CartProduct> findAllByIdWithProductAndUserId(@Param("cartProductIds") List<Long> cartProductIds,
		@Param("userId") Long userId);

	@Query("""
		select cp from CartProduct cp
		join fetch cp.product p
		join fetch cp.productOption po
		where cp.cart.id = :cartId
		and cp.deletedAt is null and p.deletedAt is null and po.deletedAt is null
		""")
	List<CartProduct> findByCartIdWithProductAndOption(@Param("cartId") Long cartId);

	@Query("""
		select cp from CartProduct cp
		join fetch cp.product p
		join fetch cp.productOption po
		where cp.cart.user = :user and cp.isChecked = true
		and cp.deletedAt is null and p.deletedAt is null and po.deletedAt is null
		""")
	List<CartProduct> findCheckedByUser(@Param("user") com.commerce.user.domain.User user);

	@Query("""
			select new com.commerce.order.dto.OrderItemDTO(
				cp.id,
				cp.quantity,
				p.price + po.additionalPrice,
				(p.price + po.additionalPrice) * cp.quantity,
				mi.storeFileName,
				p.name,
				po.name
			) from CartProduct cp
			join cp.product p
			left join p.mainImage mi
			join cp.productOption po
			where cp.id in (:cartProductIds)
			and cp.deletedAt is null and p.deletedAt is null and po.deletedAt is null
		""")
	List<OrderItemDTO> findOrderItemDTO(List<Long> cartProductIds);
}
