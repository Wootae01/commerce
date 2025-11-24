package com.commerce.controller;

import com.commerce.domain.User;
import com.commerce.dto.UserDTO;
import com.commerce.mapper.UserMapper;
import com.commerce.service.UserService;
import com.commerce.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final SecurityUtil securityUtil;
    private final UserMapper userMapper;
    private final UserService userService;

    @GetMapping("/edit")
    public String viewEditUser(Model model) {
        User user = securityUtil.getCurrentUser();
        model.addAttribute("user", userMapper.toUserDTO(user));

        return "my-info";
    }

    @PostMapping("/edit")
    public String editInfo(UserDTO dto) {

        User user = securityUtil.getCurrentUser();
        user.updateInfo(dto);

        userService.save(user);

        return "redirect:/";
    }
}
