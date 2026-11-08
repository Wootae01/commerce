package com.commerce.admin.dto;

import java.time.LocalDateTime;

import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.PaymentType;
import com.commerce.order.dto.AdminOrderRow;

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
	private int finalPrice;
	private OrderStatus orderStatus;

	public static AdminOrderListResponseDTO from(AdminOrderRow row) {
		return AdminOrderListResponseDTO.builder()
			.id(row.orderId())
			.buyerName(row.buyerName())
			.orderPhone(row.receiverPhone())
			.paymentType(PaymentType.textOf(row.paymentType()))
			.orderDate(row.orderDate())
			.orderNumber(row.orderNumber())
			.orderStatus(row.orderStatus())
			.finalPrice(row.finalPrice())
			.build();
	}
}
