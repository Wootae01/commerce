package com.commerce.order.domain;

import com.commerce.common.domain.BaseEntity;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.OrderType;
import com.commerce.common.enums.PaymentType;
import com.commerce.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Orders extends BaseEntity{

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
}
