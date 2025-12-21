package com.commerce.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
@Setter
public class CancelResponseDTO {
	boolean success;

	private String orderNumber;
	private LocalDateTime canceledAt;
	private int cancelAmount;
	private String refundMethod;

	private String errorMessage;
}
