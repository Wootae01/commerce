package com.commerce.service;

import com.commerce.domain.User;
import com.commerce.exception.EntityNotFoundException;
import com.commerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("등록된 사용자가 아닙니다."));
    }

    public void save(User user) {
        userRepository.save(user);
    }
}
