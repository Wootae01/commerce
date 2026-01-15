package com.commerce.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
    public String home(@RequestParam(defaultValue = "featured") String tab, Model model) {

        List<ProductHomeDTO> homeProducts = null;
        switch(tab) {
            case "popular" -> homeProducts = productService.findPopularProductHome(30, 20);
            case "all" -> homeProducts = productService.findHomeProducts();
            case "featured" -> homeProducts = productService.findFeaturedProducts();
            default -> {
                tab = "featured";
                homeProducts = productService.findFeaturedProducts();
            }
        }

        model.addAttribute("products", homeProducts);
        model.addAttribute("tab", tab);

        return "home";
    }
}
