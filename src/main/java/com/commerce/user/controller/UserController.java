package com.commerce.user.controller;

import com.commerce.user.domain.User;
import com.commerce.user.dto.UserDTO;
import com.commerce.user.service.UserService;
import com.commerce.common.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final SecurityUtil securityUtil;
    private final UserService userService;

    @GetMapping("/edit")
    public String viewEditUser(Model model) {
        User user = securityUtil.getCurrentUser();
        model.addAttribute("user", UserDTO.from(user));

        return "my-info";
    }

    @PostMapping("/edit")
    public String editInfo(@Valid @ModelAttribute("user") UserDTO dto, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "my-info";
        }

        User user = securityUtil.getCurrentUser();
        user.updateInfo(dto.getName(), dto.getPhone(), dto.getAddress(), dto.getAddressDetail(), dto.getEmail());

        userService.save(user);

        return "redirect:/";
    }
}
