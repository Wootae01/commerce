package com.commerce.order.domain;

import com.commerce.common.domain.BaseEntity;
import com.commerce.common.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Delivery extends BaseEntity{
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
