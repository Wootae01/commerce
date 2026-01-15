package com.commerce.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;

@Entity
@Getter
public class Product extends BaseEntity{

    @Id
    @Column(name = "product_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Admin admin;

    @OneToMany(mappedBy = "product", orphanRemoval = true, cascade = CascadeType.ALL)
    private List<Image> images = new ArrayList<>();

    private int price;

    private String name;
    private int stock;
    private String description;

    private boolean featured = false;
    private Integer featuredRank;


    public Product() {}

    public Product(Admin admin, int price, String name, int stock, String description) {
        this.admin = admin;
        this.price = price;
        this.name = name;
        this.stock = stock;
        this.description = description;
    }

    public void update(int price, int stock, String name, String description) {
        this.price = price;
        this.stock = stock;
        this.name = name;
        this.description = description;
    }

    public void addImage(Image image) {
        images.add(image);
        image.setProduct(this);
    }
}