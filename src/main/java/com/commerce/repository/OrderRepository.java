package com.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.commerce.domain.Orders;

public interface OrderRepository extends JpaRepository<Orders, Long> {
}
