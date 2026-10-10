package com.commerce.product.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.commerce.common.enums.OrderStatus;

import com.commerce.admin.dto.AdminProductListDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.commerce.product.domain.Product;
import com.commerce.product.dto.ProductHomeDTO;
import com.commerce.product.dto.ProductMainImageRow;

public interface ProductRepository extends JpaRepository<Product, Long> {

	// 주문 내역 이미지 조회용. 삭제된 상품도 지난 주문에서 보여야 하므로 거르지 않는다.
	@Query("""
	  select new com.commerce.product.dto.ProductMainImageRow(p.id, mi.storeFileName)
	  from Product p
	  left join p.mainImage mi
	  where p.id in :productIds
	""")
	List<ProductMainImageRow> findMainImages(@Param("productIds") List<Long> productIds);

	@Query(value = """
			select new com.commerce.product.dto.ProductHomeDTO(p.id, mi.storeFileName, p.name, p.price)
			from Product p
			left join p.mainImage mi
			where p.deletedAt is null
		""",
		countQuery = """
				select count(p) from Product p where p.deletedAt is null
			""")
	Page<ProductHomeDTO> findHomeProducts(Pageable pageable);

	@Query("""
			select new com.commerce.product.dto.ProductHomeDTO(p.id, mi.storeFileName, p.name, p.price)
			from Product p
			left join p.mainImage mi
			where p.id in :productIds
			and p.deletedAt is null
			order by p.createdAt desc
		""")
	List<ProductHomeDTO> findHomeProductsByIds(List<Long> productIds);

	@Query("""
			select new com.commerce.product.dto.ProductHomeDTO(p.id, mi.storeFileName, p.name, p.price)
			from Product p
			left join p.mainImage mi
			where p.featured = true
			and p.deletedAt is null
			order by p.featuredRank asc
		""")
	List<ProductHomeDTO> findHomeProductsByFeatured();

	@Query(value = """
				select new com.commerce.admin.dto.AdminProductListDTO(p.id, p.name, p.price, coalesce(sum(o.stock), 0), mi.storeFileName, p.createdAt, p.featured, p.featuredRank)
				from Product p
				left join p.mainImage mi
				left join p.options o on o.deletedAt is null
				where p.deletedAt is null
				group by p.id, p.name, p.price, mi.storeFileName, p.createdAt, p.featured, p.featuredRank
		""", countQuery = "select count(p) from Product p where p.deletedAt is null")
	Page<AdminProductListDTO> findAdminProductListDTO(Pageable pageable);

	@Query("""
				select p from Product p
				left join fetch p.mainImage
				left join fetch p.images
				where p.id = :productId
				and p.deletedAt is null
			""")
	Optional<Product> findByIdWithImage(Long productId);

	// 삭제된 옵션도 함께 가져온다. 판매 중인 옵션은 Product.getActiveOptions()로 거른다.
	@Query("""
				select p from Product p
				left join fetch p.options
				where p.id = :productId
				and p.deletedAt is null
			""")
	Optional<Product> findByIdWithOptions(@Param("productId") Long productId);

	@Query(value = """
		select new com.commerce.product.dto.ProductHomeDTO(p.id, mi.storeFileName, p.name, p.price, p.createdAt)
		from Product p
		left join p.mainImage mi
		where (:keyword is null or p.name like %:keyword%)
		and (:minPrice is null or p.price >= :minPrice)
		and (:maxPrice is null or p.price <= :maxPrice)
		and p.deletedAt is null
""", countQuery = """
	select count(distinct p) from Product p
		where (:keyword is null or p.name like %:keyword%)
		and (:minPrice is null or p.price >= :minPrice)
		and (:maxPrice is null or p.price <= :maxPrice)
		and p.deletedAt is null
""")
	Page<ProductHomeDTO> searchProducts(String keyword, Integer minPrice, Integer maxPrice, Pageable pageable);

	@Query(value = """
	select new com.commerce.product.dto.ProductHomeDTO(p.id, mi.storeFileName, p.name, p.price, p.createdAt)
	from Product p
	left join p.mainImage mi
	left join OrderProduct op on op.product = p
	left join op.order o on o.orderStatus in :statuses and o.approvedAt >= :since
	where (:keyword is null or p.name like %:keyword%)
		and (:minPrice is null or p.price >= :minPrice)
		and (:maxPrice is null or p.price <= :maxPrice)
		and p.deletedAt is null
	group by p.id, mi.storeFileName, p.name, p.price, p.createdAt
	order by coalesce(sum(op.quantity), 0) desc
""", countQuery = """
	select count(distinct p) from Product p
			where (:keyword is null or p.name like %:keyword%)
			and (:minPrice is null or p.price >= :minPrice)
			and (:maxPrice is null or p.price <= :maxPrice)
			and p.deletedAt is null
""")
	Page<ProductHomeDTO> searchProductBySales(String keyword, Integer minPrice,
											  Integer maxPrice, LocalDateTime since,
											  List<OrderStatus> statuses,
											  Pageable pageable);
}
