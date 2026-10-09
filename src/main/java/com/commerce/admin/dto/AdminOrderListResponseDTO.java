package com.commerce.admin.dto;

import java.time.LocalDateTime;

import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.PaymentType;
import com.commerce.order.domain.Orders;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class AdminOrderListResponseDTO {
	private Long id;
	private String orderNumber;
	private LocalDateTime orderDate;
	private String buyerName;
	private String paymentType;
	private String orderPhone;
	private int totalPrice;
	private OrderStatus orderStatus;

	public static AdminOrderListResponseDTO from(Orders order) {
		PaymentType paymentType = order.getPaymentType();

		return AdminOrderListResponseDTO.builder()
			.id(order.getId())
			.buyerName(order.getUser().getName())
			.orderPhone(order.getReceiverPhone())
			.paymentType(paymentType == null ? PaymentType.UNKNOWN.getText() : paymentType.getText())
			.orderDate(order.getCreatedAt())
			.orderNumber(order.getOrderNumber())
			.orderStatus(order.getOrderStatus())
			.totalPrice(order.getFinalPrice())
			.build();
	}
}
