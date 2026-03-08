package com.commerce.service;

import org.springframework.stereotype.Service;

import com.commerce.exception.EntityNotFoundException;

import com.commerce.domain.Admin;
import com.commerce.repository.AdminRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {
	private final AdminRepository adminRepository;

	public Admin findByUsername(String username) {
		return adminRepository.findByUsername(username)
			.orElseThrow(() -> new EntityNotFoundException("해당 관리자 username 을 찾을 수 없습니다."));
	}
}
