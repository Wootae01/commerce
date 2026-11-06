package com.commerce.payment.domain;

import java.time.LocalDateTime;

import com.commerce.common.domain.BaseUpdatableEntity;
import com.commerce.common.enums.PaymentType;
import com.commerce.order.domain.Orders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 하나의 토스 결제. 결제 확정을 시작할 때(READY → PAYMENT_PENDING) 만들어진다.
 * 결제를 시도하지 않은 주문에는 없다. 주문 상태는 Orders가 갖고, 이 엔티티는 토스에서 받은 사실만 갖는다.
 * 바꿀 때는 주문 행 락을 잡은 트랜잭션 안에서만 바꾼다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseUpdatableEntity {

	@Id
	@Column(name = "payment_id")
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 주문에서 결제로는 연결하지 않는다. 반대쪽 @OneToOne(mappedBy)은 지연 로딩이 안 돼 주문 목록에서 N+1이 생긴다
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", unique = true, nullable = false)
	private Orders order;

	// 응답을 모를 때 같은 값으로 승인을 다시 요청한다
	@Column(unique = true, nullable = false)
	private String paymentKey;

	// 승인 요청한 금액
	@Column(nullable = false)
	private int amount;

	// 승인되면 채운다
	@Enumerated(EnumType.STRING)
	private PaymentType paymentType;

	// 토스 결제 승인 시각. 승인되면 채운다
	private LocalDateTime approvedAt;

	// 결제 실패 이유. 결제 실패로 주문이 취소될 때만 채운다
	// 코드: 토스 에러 코드 또는 조회 상태(ABORTED 등). 사용자에게는 보여주지 않는다
	private String failureCode;

	// 사용자에게 보여줄 문장
	@Column(length = FAILURE_MESSAGE_MAX_LENGTH)
	private String failureMessage;

	private static final int FAILURE_MESSAGE_MAX_LENGTH = 255;

	private Payment(Orders order, String paymentKey, int amount) {
		this.order = order;
		this.paymentKey = paymentKey;
		this.amount = amount;
	}

	public static Payment start(Orders order, String paymentKey) {
		return new Payment(order, paymentKey, order.getFinalPrice());
	}

	public void approve(PaymentType paymentType, LocalDateTime approvedAt) {
		this.paymentType = paymentType;
		this.approvedAt = approvedAt;
	}

	// 문장이 컬럼 길이보다 길면 잘라서 남긴다. 길다고 취소 반영이 실패하면 안 된다
	public void fail(String failureCode, String failureMessage) {
		this.failureCode = failureCode;
		this.failureMessage = failureMessage != null && failureMessage.length() > FAILURE_MESSAGE_MAX_LENGTH
			? failureMessage.substring(0, FAILURE_MESSAGE_MAX_LENGTH)
			: failureMessage;
	}
}
