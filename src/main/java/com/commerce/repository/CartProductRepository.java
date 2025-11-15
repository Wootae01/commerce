package com.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.commerce.domain.CartProduct;

public interface CartProductRepository extends JpaRepository<CartProduct, Long> {
}
