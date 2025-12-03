package com.commerce.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OrderDetailResponseDTO {

	// 상단: 주문 기본 정보

	private String orderNumber;    // 주문번호
	private LocalDateTime orderDate; // 주문일자

	// 주문자 정보 블록
	private OrdererInfo ordererInfo;

	// 배송지 정보 블록
	private ShippingInfo shippingInfo;

	// 결제 정보 블록
	private PaymentInfo paymentInfo;

	// 주문 상품 목록
	private List<OrderItemDTO> orderItems;

	private OrderPriceDTO orderPrice;


	// ================== 내부 DTO ==================

	@Getter
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	@Builder
	public static class OrdererInfo {
		private String name;      // 주문자 이름
		private String phone;     // 주문자 연락처 (010-1234-5678)
		private String email;     // 주문자 이메일 (123@naver.com)
	}

	@Getter
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	@Builder
	public static class ShippingInfo {
		private String receiverName;     // 받는 분
		private String receiverPhone;    // 연락처
		private String address;          // 기본 주소
		private String addressDetail;    // 상세 주소
		private String requestMessage;   // 요청사항
	}

	@Getter
	@NoArgsConstructor(access = AccessLevel.PROTECTED)
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	@Builder
	public static class PaymentInfo {
		private String paymentMethod;    // 결제 수단 (카드 결제 등)
		private String orderStatus;    // 주문 상태 (결제 완료 등)
	}
}

