package com.commerce.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
public class Cart extends BaseEntity{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long id;

    @JoinColumn(name = "user_id")
    @OneToOne(fetch = FetchType.LAZY)
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    List<CartProduct> cartProducts = new ArrayList<>();

    public void addProduct(CartProduct cartProduct) {
        cartProducts.add(cartProduct);
        cartProduct.setCart(this);
    }

    public void deleteProduct(CartProduct cartProduct) {
        cartProducts.remove(cartProduct);
        cartProduct.setCart(null);
    }


    public Cart() {
    }

    public Cart(User user) {
        this.user = user;
    }
}
