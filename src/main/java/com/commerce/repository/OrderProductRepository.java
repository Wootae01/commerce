package com.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.commerce.domain.OrderProduct;

public interface OrderProductRepository extends JpaRepository<OrderProduct, Long> {
}
