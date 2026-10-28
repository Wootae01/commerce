package com.commerce.payment.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.PaymentType;
import com.commerce.order.domain.Orders;

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

	public static PaySuccessDTO from(Orders order) {
		LocalDateTime time = order.getApprovedAt();
		String approvedAtText = time != null
			? time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
			: "-";
		PaymentType paymentType = order.getPaymentType();

		return new PaySuccessDTO(order.getOrderNumber(), order.getFinalPrice(),
			paymentType == null ? PaymentType.UNKNOWN.getText() : paymentType.getText(), approvedAtText,
			order.getOrderStatus());
	}
}
