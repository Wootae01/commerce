package com.commerce.product.repository;


import com.commerce.product.domain.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    // 판매 중인 옵션만 조회 (상품 상세)
    @Query("select o from ProductOption o where o.product.id = :productId and o.deletedAt is null")
    List<ProductOption> findByProductId(@Param("productId") Long productId);
}
