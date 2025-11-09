package com.commerce.domain;

import com.commerce.domain.enums.RoleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Primary;

@Table
@Entity
@Getter
@Setter
public class User extends BaseEntity{

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
    private String email;

}
