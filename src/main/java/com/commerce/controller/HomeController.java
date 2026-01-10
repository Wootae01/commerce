package com.commerce.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.commerce.dto.ProductHomeDTO;
import com.commerce.service.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@Slf4j
public class HomeController {

    private final ProductService productService;

    // 홈화면
    @GetMapping("/")
    public String home(Model model) {

        List<ProductHomeDTO> homeProducts = productService.findHomeProducts();
        log.info("home products: {}", homeProducts);
        if (!homeProducts.isEmpty()) {
            model.addAttribute("products", homeProducts);
        }

        return "home";
    }
}
