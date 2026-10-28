package com.commerce.order.domain;

import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.domain.BaseUpdatableEntity;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.OrderType;
import com.commerce.common.enums.PaymentType;
import com.commerce.common.exception.ApiException;
import com.commerce.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Orders extends BaseUpdatableEntity {

    @Id
    @Column(name = "order_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String orderNumber;

    @Column(unique = true)
    private String paymentKey;

    @JoinColumn(name = "user_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderProduct> orderProducts = new ArrayList<>();

    private int finalPrice;

    private String orderName;
    private String receiverName;
    private String receiverAddress;
    private String orderAddressDetail;
    private String receiverPhone;
    private String requestNote;

    @Enumerated(EnumType.STRING)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;

    @Enumerated(EnumType.STRING)
    private OrderType orderType;

    /**
     * 장바구니 주문일 때 연결된 CartProduct ID 목록.
     * 결제 승인 후 해당 장바구니 상품들을 삭제하는 데 사용한다.
     * BUY_NOW 주문에서는 비어 있다.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "order_cart_product",
        joinColumns = @JoinColumn(name = "order_id")
    )
    @Column(name = "cart_product_id")
    private List<Long> cartProductIds = new ArrayList<>();

    private LocalDateTime approvedAt; // Toss Payments 결제 승인 시각

    @Builder
    private Orders(User user, String orderName, int finalPrice, String receiverName, String receiverAddress, String orderNumber,
        String receiverPhone, String orderAddressDetail, String requestNote, PaymentType paymentType, OrderStatus orderStatus, OrderType orderType) {
        this.orderNumber = orderNumber;
        this.user = user;
        this.orderName = orderName;
        this.finalPrice = finalPrice;
        this.receiverName = receiverName;
        this.receiverAddress = receiverAddress;
        this.receiverPhone = receiverPhone;
        this.paymentType = paymentType;
        this.orderStatus = orderStatus;
        this.orderAddressDetail = orderAddressDetail;
        this.requestNote = requestNote;
        this.orderType = orderType;
    }

    // 결제 확정 시작: READY → PAYMENT_PENDING. 응답을 모를 때 같은 값으로 승인을 다시 요청할 수 있게 paymentKey를 저장한다
    public void startPayment(String paymentKey) {
        if (orderStatus != OrderStatus.READY) {
            throw new ApiException(GeneralResponseCode.PAYMENT_ALREADY_PROCESSED);
        }
        this.paymentKey = paymentKey;
        this.orderStatus = OrderStatus.PAYMENT_PENDING;
    }

    // 결제 성공 반영: PAYMENT_PENDING → PAID
    public boolean markPaid(String paymentKey, PaymentType paymentType, LocalDateTime approvedAt) {
        if (orderStatus != OrderStatus.PAYMENT_PENDING) {
            return false;
        }
        this.paymentKey = paymentKey;
        this.paymentType = paymentType;
        this.approvedAt = approvedAt;
        this.orderStatus = OrderStatus.PAID;
        return true;
    }

    // 결제 실패 반영: PAYMENT_PENDING → CANCELED
    public boolean cancelPending() {
        if (orderStatus != OrderStatus.PAYMENT_PENDING) {
            return false;
        }
        this.orderStatus = OrderStatus.CANCELED;
        return true;
    }

    // 취소 시작: PAID·READY → CANCEL_REQUESTED
    public void requestCancel() {
        if (!(orderStatus == OrderStatus.PAID || orderStatus == OrderStatus.READY)) {
            throw new ApiException(GeneralResponseCode.ORDER_CANCEL_NOT_ALLOWED);
        }
        this.orderStatus = OrderStatus.CANCEL_REQUESTED;
    }

    // 취소 완료: CANCEL_REQUESTED → CANCELED
    public void completeCancel() {
        if (orderStatus != OrderStatus.CANCEL_REQUESTED) {
            throw new ApiException(GeneralResponseCode.ORDER_CANCEL_NOT_ALLOWED, "취소 처리할 수 없는 상태입니다.");
        }
        this.orderStatus = OrderStatus.CANCELED;
    }

    // 토스 취소 실패 시 복원: CANCEL_REQUESTED → PAID
    public boolean revertCancel() {
        if (orderStatus != OrderStatus.CANCEL_REQUESTED) {
            return false;
        }
        this.orderStatus = OrderStatus.PAID;
        return true;
    }

    // 관리자 배송 상태 변경: PAID → PREPARING → SHIPPING → DELIVERED (한 단계씩)
    public void advanceDelivery(OrderStatus target) {
        if (target == null || nextDeliveryStatus() != target) {
            throw new ApiException(GeneralResponseCode.ORDER_STATUS_CHANGE_NOT_ALLOWED);
        }
        this.orderStatus = target;
    }

    private OrderStatus nextDeliveryStatus() {
        return switch (orderStatus) {
            case PAID -> OrderStatus.PREPARING;
            case PREPARING -> OrderStatus.SHIPPING;
            case SHIPPING -> OrderStatus.DELIVERED;
            default -> null;
        };
    }
}
