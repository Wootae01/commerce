package com.commerce.mapper;

import com.commerce.domain.User;
import com.commerce.dto.UserDTO;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDTO toUserDTO(User user) {
        UserDTO dto = new UserDTO(
                user.getName(), user.getPhone(), user.getAddress(), user.getEmail()
        );

        return dto;
    }
}
