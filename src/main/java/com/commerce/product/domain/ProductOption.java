package com.commerce.product.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor
public class ProductOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;    // 옵션 이름

    private int stock;
    private int additionalPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @Setter
    private Product product;

    public ProductOption(String name, int stock, int additionalPrice) {
        this.name = name;
        this.stock = stock;
        this.additionalPrice = additionalPrice;
    }

    public void deductStock(int quantity) {
        this.stock -= quantity;
    }

    public void update(String name, int stock, int additionalPrice) {
        this.name = name;
        this.stock = stock;
        this.additionalPrice = additionalPrice;
    }
}
