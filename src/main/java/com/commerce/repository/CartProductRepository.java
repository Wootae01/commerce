package com.commerce.repository;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.commerce.domain.CartProduct;
import com.commerce.dto.CartProductDTO;
import com.commerce.dto.OrderItemDTO;

public interface CartProductRepository extends JpaRepository<CartProduct, Long> {

	@Query("select cp.id from CartProduct cp where cp.cart.id = :cartId")
	List<Long> findIdsByCartId(Long cartId);

	@Modifying
	@Query("delete from CartProduct cp where cp.id in :ids and cp.cart.user.id = :userId")
	int deleteSelectedFromUserCart(@Param("ids") List<Long> ids, @Param("userId") Long userId);

	@Query("""
		select new com.commerce.dto.CartProductDTO (
			cp.id,
			cp.isChecked,
			cp.quantity,
			p.price,
			mi.storeFileName,
			p.name
		)
		from CartProduct cp
		join cp.product p
		left join p.mainImage mi
		where cp.cart.id = :cartId
		""")
	List<CartProductDTO> findCartRows(Long cartId);

	@Query("select cp from CartProduct cp join fetch cp.product p left join fetch p.mainImage where cp.id in (:cartProductIds)")
	List<CartProduct> findAllByIdWithProduct(List<Long> cartProductIds);

	@Query("""
			select new com.commerce.dto.OrderItemDTO(
				cp.id,
				cp.quantity,
				p.price,
				p.price * cp.quantity,
				mi.storeFileName,
				p.name
			) from CartProduct cp
			join cp.product p
			left join p.mainImage mi
			where cp.id in (:cartProductIds)
		""")
	List<OrderItemDTO> findOrderItemDTO(List<Long> cartProductIds);
}
