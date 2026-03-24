package com.commerce.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.commerce.product.domain.Image;

public interface ImageRepository extends JpaRepository<Image, Long> {
}
