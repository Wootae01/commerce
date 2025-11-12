package com.commerce.domain;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.validator.constraints.Range;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
public class Product extends BaseEntity{

    @Id
    @Column(name = "product_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "product", orphanRemoval = true, cascade = CascadeType.ALL)
    private List<Image> images = new ArrayList<>();

    @NotNull
    @Range(min = 100, max = 1000000000)
    private int price;

    @NotBlank
    private String name;

    @NotNull
    private int stock;
    private String description;

    public Product() {}

    public Product(int price, String name, int stock, String description) {
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

    public void addImages(List<Image> images) {
        for (Image image : images) {
            addImage(image);
        }
    }
}