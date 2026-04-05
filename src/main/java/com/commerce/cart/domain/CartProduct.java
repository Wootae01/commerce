package com.commerce.cart.domain;

import com.commerce.common.domain.BaseEntity;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
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

    @JoinColumn(name = "product_option_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private ProductOption productOption;

    private int quantity;
    private boolean isChecked;

    public CartProduct() {
    }

    public CartProduct(Cart cart, Product product, ProductOption productOption, int quantity, boolean isChecked) {
        this.cart = cart;
        this.product = product;
        this.productOption = productOption;
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
