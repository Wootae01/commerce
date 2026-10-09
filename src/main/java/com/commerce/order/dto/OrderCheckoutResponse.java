package com.commerce.order.dto;

import java.util.List;

import com.commerce.common.enums.OrderType;
import com.commerce.user.domain.User;

/**
 * 주문서 화면에 필요한 값. 주문자 기본 정보는 배송지 입력란의 초기값으로 쓴다.
 */
public record OrderCheckoutResponse(
	OrderType orderType,
	List<OrderItemDTO> items,
	Orderer orderer,
	OrderPriceDTO orderPrice,
	String tossClientKey
) {
	public record Orderer(
		String name,
		String phone,
		String address,
		String addressDetail,
		String customerKey
	) {
		public static Orderer from(User user) {
			return new Orderer(user.getName(), user.getPhone(), user.getAddress(), user.getAddressDetail(),
				user.getCustomerPaymentKey());
		}
	}
}
