package com.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.commerce.domain.Image;

public interface ImageRepository extends JpaRepository<Image, Long> {
}
