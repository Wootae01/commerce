package com.commerce.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.commerce.domain.Orders;

public interface OrderRepository extends JpaRepository<Orders, Long> {
	Optional<Orders> findByOrderNumber(String orderNumber);

}
