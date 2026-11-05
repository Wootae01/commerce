package com.commerce.payment.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.PaymentType;
import com.commerce.order.domain.Orders;
import com.commerce.payment.domain.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaySuccessDTO {
	private String orderNumber;
	private int amount;       // finalPrice
	private String method;     // 결제수단
	private String approvedAt; // 화면 표시용 문자열( 2025-12-20 15:20:42)
	private OrderStatus orderStatus; // 결제 대기 화면이 확정 결과를 폴링할 때 본다

	// payment는 결제를 시도하지 않은 주문이면 null
	public static PaySuccessDTO from(Orders order, Payment payment) {
		LocalDateTime time = payment == null ? null : payment.getApprovedAt();
		String approvedAtText = time != null
			? time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
			: "-";

		return new PaySuccessDTO(order.getOrderNumber(), order.getFinalPrice(),
			PaymentType.textOf(payment == null ? null : payment.getPaymentType()), approvedAtText,
			order.getOrderStatus());
	}
}
