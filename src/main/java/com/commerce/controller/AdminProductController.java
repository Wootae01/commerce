package com.commerce.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.commerce.domain.Product;
import com.commerce.service.ProductService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/products")
public class AdminProductController {

	private final ProductService productService;

	@GetMapping
	public String productManagement(Model model) {
		List<Product> products = productService.findAll();
		model.addAttribute("products", products);

		return "/admin/products";
	}

	// 상품 등록
	@GetMapping("/new")
	public String registerPage(Model model) {
		model.addAttribute("product", new Product());
		return "/admin/product-new";
	}

	@PostMapping("/new")
	public String registerProduct(Product product) {
		productService.save(product);
		return "redirect:/admin/dashboard";
	}

	// 상품 수정
	@PostMapping("/edit/{id}")
	public String update(@PathVariable Long id, Product updatedProduct) {
		productService.update(id, updatedProduct);
		return "redirect:/admin/dashboard";
	}

	// 상품 삭제
	@PostMapping("/delete/{id}")
	public String delete(@PathVariable Long id) {
		productService.delete(id);
		return "redirect:/admin/dashboard";
	}
}
