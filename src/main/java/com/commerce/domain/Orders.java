package com.commerce.domain;

import com.commerce.domain.enums.OrderStatus;
import com.commerce.domain.enums.OrderType;
import com.commerce.domain.enums.PaymentType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
