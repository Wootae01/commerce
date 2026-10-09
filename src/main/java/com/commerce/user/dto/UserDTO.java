package com.commerce.user.dto;

import com.commerce.user.domain.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UserDTO {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "^010\\d{8}$")
    private String phone;

    private String address;
    private String addressDetail;
    @NotBlank
    @Email
    private String email;

    private UserDTO(String name, String phone, String address, String addressDetail, String email) {
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.addressDetail = addressDetail;
        this.email = email;
    }

    public static UserDTO from(User user) {
        return new UserDTO(user.getName(), user.getPhone(), user.getAddress(), user.getAddressDetail(),
                user.getEmail());
    }
}
