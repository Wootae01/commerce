package com.commerce.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public enum SalesPeriod {
    WEEK("최근 1주일", 7),
    MONTH("최근 1개월", 30),
    SIX_MONTHS("최근 6개월", 180);

    private final String label;
    private final int days;

    public LocalDateTime getStartDate() {
        return LocalDateTime.now().minusDays(days);
    }
}
