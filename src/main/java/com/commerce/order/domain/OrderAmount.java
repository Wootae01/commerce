package com.commerce.order.domain;

import com.commerce.product.domain.DeliveryPolicy;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * 주문에 저장하는 금액: 배송비, 결제 금액(= 상품 합계 + 배송비, 토스에 승인 요청하는 금액).
 * 상품 합계는 저장하지 않고 주문 상품에서 구한다({@link Orders#getProductsTotal()}).
 * 배송 정책은 {@link #forNewOrder(int)}에서만 읽는다.
 * 할인 같은 금액 항목이 생기면 이 타입에 더하고 결제 금액 계산을 같이 고친다.
 */
@Embeddable
public record OrderAmount(
	// 주문할 때의 배송 정책으로 정한 배송비. 정책이 바뀌어도 이 주문의 배송비는 그대로다
	@Column(name = "delivery_fee", nullable = false) int deliveryFee,
	@Column(name = "final_price", nullable = false) int finalPrice
) {

	// 새 주문의 금액: 지금 배송 정책으로 배송비를 정하고, 결제 금액 = 상품 합계 + 배송비
	public static OrderAmount forNewOrder(int productsTotal) {
		int deliveryFee = DeliveryPolicy.DELIVERY_FEE;
		return new OrderAmount(deliveryFee, productsTotal + deliveryFee);
	}
}
