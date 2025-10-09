package com.commerce.domain;

import com.commerce.domain.enums.DeliveryStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class Delivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "order_id")
    @OneToOne(fetch = FetchType.LAZY)
    private Orders order;

    private String address;
    private DeliveryStatus status;
    private Long trackingNumber;
    private LocalDateTime deliveryDate;
}
