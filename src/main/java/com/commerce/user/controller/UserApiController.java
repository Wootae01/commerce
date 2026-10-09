package com.commerce.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.dto.GeneralResponse;
import com.commerce.common.util.SecurityUtil;
import com.commerce.user.domain.User;
import com.commerce.user.dto.UserDTO;
import com.commerce.user.dto.UserMapper;
import com.commerce.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me")
public class UserApiController {

	private final SecurityUtil securityUtil;
	private final UserMapper userMapper;
	private final UserService userService;

	@GetMapping
	public ResponseEntity<GeneralResponse<UserDTO>> getMyInfo() {
		User user = securityUtil.getCurrentUser();
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, userMapper.toUserDTO(user));
	}

	@PutMapping
	public ResponseEntity<GeneralResponse<UserDTO>> updateMyInfo(@Valid @RequestBody UserDTO request) {
		User user = securityUtil.getCurrentUser();
		user.updateInfo(request);
		userService.save(user);
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, userMapper.toUserDTO(user));
	}
}
