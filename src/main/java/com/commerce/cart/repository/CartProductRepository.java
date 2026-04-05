package com.commerce.cart.repository;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.commerce.cart.domain.CartProduct;
import com.commerce.cart.dto.CartProductDTO;
import com.commerce.order.dto.OrderItemDTO;

public interface CartProductRepository extends JpaRepository<CartProduct, Long> {

	@Query("select cp.id from CartProduct cp where cp.cart.id = :cartId")
	List<Long> findIdsByCartId(Long cartId);

	@Modifying
	@Query("delete from CartProduct cp where cp.id in :ids and cp.cart.user.id = :userId")
	int deleteSelectedFromUserCart(@Param("ids") List<Long> ids, @Param("userId") Long userId);

	@Query("""
		select new com.commerce.cart.dto.CartProductDTO (
			cp.id,
			cp.isChecked,
			cp.quantity,
			p.price + coalesce(po.additionalPrice, 0),
			mi.storeFileName,
			p.name,
			po.name
		)
		from CartProduct cp
		join cp.product p
		left join p.mainImage mi
		left join cp.productOption po
		where cp.cart.id = :cartId
		""")
	List<CartProductDTO> findCartRows(Long cartId);

	@Query("select cp from CartProduct cp join fetch cp.product p left join fetch p.mainImage left join fetch cp.productOption where cp.id in (:cartProductIds)")
	List<CartProduct> findAllByIdWithProduct(List<Long> cartProductIds);

	@Query("select cp from CartProduct cp join fetch cp.product left join fetch cp.productOption where cp.cart.id = :cartId")
	List<CartProduct> findByCartIdWithProductAndOption(@Param("cartId") Long cartId);

	@Query("select cp from CartProduct cp join fetch cp.product left join fetch cp.productOption where cp.cart.user = :user and cp.isChecked = true")
	List<CartProduct> findCheckedByUser(@Param("user") com.commerce.user.domain.User user);

	@Query("""
			select new com.commerce.order.dto.OrderItemDTO(
				cp.id,
				cp.quantity,
				p.price + coalesce(po.additionalPrice, 0),
				(p.price + coalesce(po.additionalPrice, 0)) * cp.quantity,
				mi.storeFileName,
				p.name,
				po.name
			) from CartProduct cp
			join cp.product p
			left join p.mainImage mi
			left join cp.productOption po
			where cp.id in (:cartProductIds)
		""")
	List<OrderItemDTO> findOrderItemDTO(List<Long> cartProductIds);
}
