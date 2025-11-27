package com.commerce.domain;

import com.commerce.domain.enums.OrderStatus;
import com.commerce.domain.enums.PaymentType;
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

    @JoinColumn(name = "user_id")
    @OneToOne(fetch = FetchType.LAZY)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderProduct> orderProducts = new ArrayList<>();

    private int total_price;
    private LocalDateTime orderDate;
    private String orderName;
    private String orderAddress;
    private String orderAddressDetail;
    private String orderPhone;
    private String requestNote;

    @Enumerated(EnumType.STRING)
    private PaymentType paymentMethod;

    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;

    @Builder
    private Orders(User user, int total_price, LocalDateTime orderDate, String orderName, String orderAddress,
        String orderPhone, String orderAddressDetail, String requestNote, PaymentType paymentMethod, OrderStatus orderStatus) {
        this.user = user;
        this.total_price = total_price;
        this.orderDate = orderDate;
        this.orderName = orderName;
        this.orderAddress = orderAddress;
        this.orderPhone = orderPhone;
        this.paymentMethod = paymentMethod;
        this.orderStatus = orderStatus;
        this.orderAddressDetail = orderAddressDetail;
        this.requestNote = requestNote;
    }
}
