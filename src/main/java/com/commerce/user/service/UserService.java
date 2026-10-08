package com.commerce.user.service;

import com.commerce.user.domain.User;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.exception.ApiException;
import com.commerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(GeneralResponseCode.USER_NOT_FOUND));
    }

    public void save(User user) {
        userRepository.save(user);
    }
}
