package com.commerce.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.commerce.dto.AdminProductListDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.commerce.domain.Product;
import com.commerce.dto.ProductHomeDTO;
import com.commerce.dto.ProductMainImageRow;

public interface ProductRepository extends JpaRepository<Product, Long> {

	@Modifying
	@Query("update Product p set p.stock = p.stock - :quantity where p.id = :id and p.stock > :quantity")
	int decreaseStock(Long id, int quantity);



	@Query("""
	  select new com.commerce.dto.ProductMainImageRow(p.id, mi.storeFileName)
	  from Product p
	  left join p.mainImage mi
	  where p.id in :productIds
	""")
	List<ProductMainImageRow> findMainImages(@Param("productIds") List<Long> productIds);

	@Query(value = """
			select new com.commerce.dto.ProductHomeDTO(p.id, mi.storeFileName, p.name, p.price)
			from Product p
			left join p.mainImage mi
		""",
		countQuery = """
				select count(p) from Product p
			""")
	Page<ProductHomeDTO> findHomeProducts(Pageable pageable);


	@Query("""
			select new com.commerce.dto.ProductHomeDTO(p.id, mi.storeFileName, p.name, p.price)
			from Product p
			left join p.mainImage mi
			where p.id in :productIds
			order by p.createdAt desc
		""")
	List<ProductHomeDTO> findHomeProductsByIds(List<Long> productIds);

	@Query("""
			select new com.commerce.dto.ProductHomeDTO(p.id, mi.storeFileName, p.name, p.price)
			from Product p
			left join p.mainImage mi
			where p.featured = true
			order by p.featuredRank asc
		""")
	List<ProductHomeDTO> findHomeProductsByFeatured();


	@Query("""
				select new com.commerce.dto.AdminProductListDTO(p.id, p.name, p.price, p.stock, mi.storeFileName, p.createdAt, p.featured, p.featuredRank)
				from Product p
				left join p.mainImage mi
		""")
	Page<AdminProductListDTO> findAdminProductListDTO(Pageable pageable);

	@Query("""
				select p from Product p
				left join fetch p.mainImage
				left join fetch p.images
				where p.id = :productId
			""")
	Optional<Product> findByIdWithImage(Long productId);

	@Query(value = """
		select new com.commerce.dto.ProductHomeDTO(p.id, mi.storeFileName, p.name, p.price, p.createdAt)
		from Product p
		left join p.mainImage mi
		where (:keyword is null or p.name like %:keyword%)
		and (:minPrice is null or p.price >= :minPrice)
		and (:maxPrice is null or p.price <= :maxPrice)
""", countQuery = """
	select count(distinct p) from Product p
		where (:keyword is null or p.name like %:keyword%)
		and (:minPrice is null or p.price >= :minPrice)
		and (:maxPrice is null or p.price <= :maxPrice)
""")
	Page<ProductHomeDTO> searchProducts(String keyword, Integer minPrice, Integer maxPrice, Pageable pageable);

	@Query(value = """
	select new com.commerce.dto.ProductHomeDTO(p.id, mi.storeFileName, p.name, p.price, p.createdAt)
	from Product p
	left join p.mainImage mi
	left join OrderProduct op on op.product = p and op.createdAt >= :since
	where (:keyword is null or p.name like %:keyword%)
		and (:minPrice is null or p.price >= :minPrice)
		and (:maxPrice is null or p.price <= :maxPrice)
	group by p.id, mi.storeFileName, p.name, p.price, p.createdAt
	order by coalesce(sum(op.quantity), 0) desc
""", countQuery = """
	select count(distinct p) from Product p
			where (:keyword is null or p.name like %:keyword%)
			and (:minPrice is null or p.price >= :minPrice)
			and (:maxPrice is null or p.price <= :maxPrice)
""")
	Page<ProductHomeDTO> searchProductBySales(String keyword, Integer minPrice,
											  Integer maxPrice, LocalDateTime since,
											  Pageable pageable);
}
