package com.commerce.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.commerce.domain.enums.OrderStatus;
import com.commerce.domain.enums.PaymentType;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminOrderSearchCond {
	private String keyword;          // 주문번호 or 이름 or 전화번호

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate startDate;     // yyyy-MM-dd

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate endDate;       // yyyy-MM-dd

	private OrderStatus orderStatus;
	private PaymentType paymentType;
}
