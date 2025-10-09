package com.commerce.domain;

import jakarta.persistence.*;

@Entity
public class Product {

    @Id
    @Column(name = "product_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int price;
    private int name;
    private int stock;
    private int img_url;
    private String description;
}
