package com.commerce.repository;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.commerce.domain.CartProduct;
import com.commerce.dto.CartProductDTO;

public interface CartProductRepository extends JpaRepository<CartProduct, Long> {

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
		left join p.images mi on mi.isMain = true
		where cp.cart.id = :cartId
		""")
	List<CartProductDTO> findCartRows(Long cartId);
}
