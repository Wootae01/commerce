package com.commerce.controller;

import com.commerce.domain.Product;
import com.commerce.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/product")
public class ProductController {

    private final ProductService productService;


    // 상품 상세 검색
    @GetMapping("/{id}")
    public String getProductDetail(@PathVariable Long id, Model model) {
        Product product = productService.findById(id);
        model.addAttribute("product", product);
        return "product-detail";
    }

    // 상품 등록
    @PostMapping("/create")
    public String create(Product product) {
        productService.save(product);
        return "redirect:/";
    }

    // 상품 수정
    @PostMapping("/update")
    public String update(Long id, Product updatedProduct) {
        productService.update(id, updatedProduct);
        return "redirect:/";
    }

    // 상품 삭제
    @PostMapping("/delete")
    public String delete(Long id) {
        productService.delete(id);
        return "redirect:/";
    }



}
