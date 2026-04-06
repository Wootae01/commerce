package com.commerce.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.Range;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductOptionDTO {

    private Long id;
    private String name;

    @Range(min = 0, max = 999999)
    private Integer stock;

    @Range(min = 0, max = 1000000000)
    private Integer additionalPrice;
}