package com.commerce.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;

@Getter
@RequiredArgsConstructor
public enum ProductSortType {
    NEWEST("최신순", Sort.by(Sort.Direction.DESC, "createdAt")),
    PRICE_ASC("낮은 가격순", Sort.by(Sort.Direction.ASC, "price")),
    PRICE_DESC("높은 가격순", Sort.by(Sort.Direction.DESC, "price")),
    BEST_SELLING("판매량순", null);

    private final String label;
    private final Sort sort;

    public boolean isBestSelling() {
        return this == BEST_SELLING;
    }
}
