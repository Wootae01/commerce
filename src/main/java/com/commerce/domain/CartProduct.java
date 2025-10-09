package com.commerce.domain;

import jakarta.persistence.*;

@Entity
public class CartProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_product_id")
    private Long id;

    @JoinColumn(name = "cart_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Cart cart;

    private int quantity;
    private int price;
}
