package com.commerce.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.commerce.domain.Product;
import com.commerce.dto.ProductHomeDTO;
import com.commerce.mapper.ProductMapper;
import com.commerce.service.ProductService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ProductService productService;
    private final ProductMapper productMapper;
    // 홈화면
    @GetMapping("/")
    public String home(Model model) {

        List<Product> products = productService.findAll();
        if (!products.isEmpty()) {
            List<ProductHomeDTO> dtos = productMapper.toHomeProductDTO(products);
            model.addAttribute("products", dtos);
        }

        return "home";
    }
}
