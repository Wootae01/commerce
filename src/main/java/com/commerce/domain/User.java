package com.commerce.domain;

import java.util.ArrayList;
import java.util.List;

import com.commerce.domain.enums.RoleType;
import com.commerce.dto.Oauth2Response;
import com.commerce.dto.UserDTO;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.context.annotation.Primary;

@Table
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, orphanRemoval = true, cascade = CascadeType.ALL)
    private List<Orders> orders = new ArrayList<>();

    private String username;
    private String customerPaymentKey; // 토스 페이먼츠 구매자 구분 값

    @Enumerated(value = EnumType.STRING)
    private RoleType role;

    private String name;
    private String phone;
    private String address;
    private String addressDetail;
    private String email;

    @Builder
    private User(String username, String customerPaymentKey, RoleType role, String name, String phone, String email) {
        this.username = username;
        this.customerPaymentKey = customerPaymentKey;
        this.role = role;
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    public void updateOauth2Info(Oauth2Response oauth2Response) {
        this.name = oauth2Response.getName();
        this.phone = oauth2Response.getPhone();
        this.email = oauth2Response.getEmail();
    }

    public void updateInfo(UserDTO dto) {
        this.name = dto.getName();
        this.phone = dto.getPhone();
        this.address = dto.getAddress();
        this.addressDetail = dto.getAddressDetail();
        this.email = dto.getEmail();
    }

}
