package com.commerce.dto;

import com.commerce.domain.enums.ProductSortType;
import com.commerce.domain.enums.SalesPeriod;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductSearchRequest {
    private String keyword;
    private Integer minPrice;
    private Integer maxPrice;
    private ProductSortType sortType = ProductSortType.NEWEST;
    private SalesPeriod salesPeriod = SalesPeriod.MONTH;

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }

    public boolean hasMinPrice() {
        return minPrice != null;
    }

    public boolean hasMaxPrice() {
        return maxPrice != null;
    }
}
