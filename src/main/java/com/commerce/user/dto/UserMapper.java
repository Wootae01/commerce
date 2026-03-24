package com.commerce.user.dto;

import com.commerce.user.domain.User;
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
