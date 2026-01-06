package com.commerce.repository;

import com.commerce.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

	@Modifying
	@Query("update Product p set p.stock = p.stock - :quantity where p.id = :id and p.stock > :quantity")
	int decreaseStock(Long id, int quantity);
}
