package com.commerce.order.dto;

import java.time.LocalDateTime;

import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.PaymentType;

// 관리자 주문 목록 한 줄. 주문·주문자·결제 수단을 쿼리 한 번으로 읽는다. paymentType은 결제 전·승인 전이면 null
public record AdminOrderRow(
	Long orderId,
	String orderNumber,
	LocalDateTime orderDate,
	String buyerName,
	PaymentType paymentType,
	String receiverPhone,
	int finalPrice,
	OrderStatus orderStatus
) {
}
