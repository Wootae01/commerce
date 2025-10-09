package com.commerce.domain;

import com.commerce.domain.enums.RoleType;
import jakarta.persistence.*;
import org.springframework.context.annotation.Primary;

@Table
@Entity
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    private String username;

    @Enumerated(value = EnumType.STRING)
    private RoleType role;

    private String name;
    private String phone;
    private String address;

}
