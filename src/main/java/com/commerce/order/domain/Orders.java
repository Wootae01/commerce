package com.commerce.order.domain;

import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.domain.BaseUpdatableEntity;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.OrderType;
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

    @JoinColumn(name = "user_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderProduct> orderProducts = new ArrayList<>();

    // 주문할 때 정한 배송비·결제 금액
    @Embedded
    private OrderAmount amount;

    private String orderName;
    private String receiverName;
    private String receiverAddress;
    private String orderAddressDetail;
    private String receiverPhone;
    private String requestNote;

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

    /**
     * 보정 스케줄러가 이 주문을 마지막으로 처리한 시각. 다음 차례를 정하는 데 쓴다.
     * updated_at(PENDING이 된 시각)이 바뀌지 않도록 엔티티가 아니라 벌크 update로만 바꾼다
     * ({@link com.commerce.order.repository.OrderRepository#markRecoveryAttempted}).
     */
    private LocalDateTime lastRecoveryAt;

    @Builder
    private Orders(User user, String orderName, OrderAmount amount, String receiverName, String receiverAddress, String orderNumber,
        String receiverPhone, String orderAddressDetail, String requestNote, OrderStatus orderStatus, OrderType orderType) {
        this.orderNumber = orderNumber;
        this.user = user;
        this.orderName = orderName;
        this.amount = amount;
        this.receiverName = receiverName;
        this.receiverAddress = receiverAddress;
        this.receiverPhone = receiverPhone;
        this.orderStatus = orderStatus;
        this.orderAddressDetail = orderAddressDetail;
        this.requestNote = requestNote;
        this.orderType = orderType;
    }

    // 결제 금액(상품 합계 + 배송비). 토스에 승인 요청하는 금액이다
    public int getFinalPrice() {
        return amount.finalPrice();
    }

    // 상품 합계: 주문 상품의 상품별 합계를 더한 값. 주문 상품에 이미 있는 값이라 따로 저장하지 않는다
    public int getProductsTotal() {
        return orderProducts.stream()
            .mapToInt(OrderProduct::getLineTotal)
            .sum();
    }

    // 결제 확정 시작: READY → PAYMENT_PENDING. 결제 정보(Payment)는 호출한 쪽이 같은 트랜잭션에서 만든다
    public void startPayment() {
        if (orderStatus != OrderStatus.READY) {
            throw new ApiException(GeneralResponseCode.PAYMENT_ALREADY_PROCESSED);
        }
        this.orderStatus = OrderStatus.PAYMENT_PENDING;
    }

    // 결제 성공 반영: PAYMENT_PENDING → PAID
    public boolean markPaid() {
        if (orderStatus != OrderStatus.PAYMENT_PENDING) {
            return false;
        }
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
