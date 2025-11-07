package com.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.commerce.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

	User findByUsername(String username);
}
