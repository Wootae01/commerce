package com.commerce.domain;

import com.commerce.domain.enums.PaymentType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Orders {

    @Id
    @Column(name = "order_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "user_id")
    @OneToOne(fetch = FetchType.LAZY)
    private User user;

    private int total_price;
    private LocalDateTime orderDate;
    private PaymentType paymentMethod;
}
