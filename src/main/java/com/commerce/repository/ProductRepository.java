package com.commerce.repository;

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
	  select new com.commerce.dto.ProductMainImageRow(p.id, i.storeFileName)
	  from Product p
	  join p.images i
	  where p.id in :productIds
		and i.isMain = true
	""")
	List<ProductMainImageRow> findMainImages(@Param("productIds") List<Long> productIds);

	@Query(value = """
			select distinct new com.commerce.dto.ProductHomeDTO(p.id, i.storeFileName, p.name, p.price)
			from Product p
			left join p.images i on i.isMain = true
		""",
		countQuery = """
				select count(p) from Product p
			""")
	Page<ProductHomeDTO> findHomeProducts(Pageable pageable);


	@Query("""
			select new com.commerce.dto.ProductHomeDTO(p.id, i.storeFileName, p.name, p.price)
			from Product p
			left join p.images i on i.isMain = true
			where p.id in :productIds
			order by p.createdAt desc 
		""")
	List<ProductHomeDTO> findHomeProductsByIds(List<Long> productIds);

	@Query("""
			select new com.commerce.dto.ProductHomeDTO(p.id, i.storeFileName, p.name, p.price)
			from Product p
			left join p.images i on i.isMain = true
			where p.featured = true
			order by p.featuredRank asc
		""")
	List<ProductHomeDTO> findHomeProductsByFeatured();


	@Query("""
				select new com.commerce.dto.AdminProductListDTO(p.id, p.name, p.price, p.stock, i.storeFileName, p.createdAt, p.featured, p.featuredRank)
				from Product p
				left join p.images i on i.isMain = true
		""")
	Page<AdminProductListDTO> findAdminProductListDTO(Pageable pageable);

	@Query("""
				select p from Product p
				left join fetch p.images i
				where p.id = :productId
			""")
	Optional<Product> findByIdWithImage(Long productId);


}
