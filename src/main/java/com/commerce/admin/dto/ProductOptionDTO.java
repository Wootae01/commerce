package com.commerce.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductOptionDTO {

    private Long id;
    private String name;
    private int stock;
    private int additionalPrice;
}