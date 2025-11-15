package com.commerce.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter

public class CartProduct extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_product_id")
    private Long id;

    @JoinColumn(name = "cart_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Cart cart;

    @JoinColumn(name = "product_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;

    private int quantity;
    private boolean isChecked;

    public CartProduct() {
    }

    public CartProduct(Cart cart, Product product, int quantity, boolean isChecked) {
        this.cart = cart;
        this.product = product;
        this.quantity = quantity;
        this.isChecked = isChecked;
    }

    public void addQuantity() {
        this.quantity += 1;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setIsChecked(boolean isChecked) {
        this.isChecked = isChecked;
    }

    protected void setCart(Cart cart) {
        this.cart = cart;
    }
}
