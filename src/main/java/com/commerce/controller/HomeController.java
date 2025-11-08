package com.commerce.controller;

import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.commerce.domain.Product;
import com.commerce.service.ProductService;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ProductService productService;
    // 홈화면
    @GetMapping("/")
    public String home(Model model) {

        List<Product> products = productService.findAll();
        if (!products.isEmpty()) {
            model.addAttribute("products", products);
        }

        return "home";
    }
}
