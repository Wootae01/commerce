package com.commerce.payment.dto;

import java.time.LocalDateTime;

import com.commerce.common.enums.OrderStatus;

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

	// 취소 요청 뒤 주문 상태. CANCELED: 취소 완료 / CANCEL_REQUESTED: 결과를 몰라 확인 중 / PAID: 토스가 거절
	private OrderStatus orderStatus;
}
