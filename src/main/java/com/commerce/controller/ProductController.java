package com.commerce.controller;

import com.commerce.domain.Product;
import com.commerce.dto.ProductDetailDTO;
import com.commerce.mapper.ProductMapper;
import com.commerce.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;

    // 상품 상세 검색
    @GetMapping("/{id}")
    public String getProductDetail(@PathVariable Long id, Model model) {
        Product product = productService.findById(id);
        ProductDetailDTO dto = productMapper.toProductDetailDTO(product);
        model.addAttribute("product", dto);
        return "product-detail";
    }

}
