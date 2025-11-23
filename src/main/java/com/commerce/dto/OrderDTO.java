package com.commerce.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderDTO {
    private String name;
    private String phone;
    private String address;
    private String addressDetail;
    private String requestNote;
    private String payment;
}
