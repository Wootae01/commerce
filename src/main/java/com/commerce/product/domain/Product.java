package com.commerce.product.domain;

import java.util.ArrayList;
import java.util.List;

import com.commerce.admin.domain.Admin;
import com.commerce.common.domain.BaseUpdatableEntity;
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
public class Product extends BaseUpdatableEntity {

    @Id
    @Column(name = "product_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Admin admin;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "main_image_id")
    private Image mainImage;

    @OneToMany(mappedBy = "product", orphanRemoval = true, cascade = CascadeType.ALL)
    private List<Image> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductOption> options = new ArrayList<>();

    private int price;

    private String name;
    private String description;

    private boolean featured = false;
    private Integer featuredRank;


    public Product() {}

    public Product(Admin admin, int price, String name, String description) {
        this.admin = admin;
        this.price = price;
        this.name = name;
        this.description = description;
    }

    public void addOption(ProductOption productOption) {
        options.add(productOption);
        productOption.setProduct(this);
    }

    // 삭제되지 않은 옵션. 삭제된 옵션은 지난 주문이 참조하므로 컬렉션에 남아 있다.
    public List<ProductOption> getActiveOptions() {
        return options.stream()
            .filter(o -> !o.isDeleted())
            .toList();
    }

    public List<Image> getActiveImages() {
        return images.stream()
            .filter(i -> !i.isDeleted())
            .toList();
    }

    // 상품을 삭제하면 옵션도 더 이상 팔 수 없으므로 함께 삭제한다.
    @Override
    public void softDelete(String deletedBy) {
        super.softDelete(deletedBy);
        getActiveOptions().forEach(o -> o.softDelete(deletedBy));
    }

    public void update(int price, String name, String description) {
        this.price = price;
        this.name = name;
        this.description = description;
    }

    public void addImage(Image image) {
        images.add(image);
        image.setProduct(this);
    }

    public void setMainImage(Image mainImage) {
        this.mainImage = mainImage;
    }
}
