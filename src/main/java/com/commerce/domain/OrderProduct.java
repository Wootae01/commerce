package com.commerce.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "order_product")
public class OrderProduct {

    @Id
    @Column(name = "order_product_id")
    private Long id;

    @JoinColumn(name = "product_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    @JoinColumn(name = "order_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Orders order;

    private int quantity;
    private int price;
}
