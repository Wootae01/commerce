package com.commerce.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.commerce.domain.Admin;
import com.commerce.domain.Product;
import com.commerce.dto.AdminProductListDTO;
import com.commerce.dto.FeaturedItem;
import com.commerce.dto.FeaturedUpdateForm;
import com.commerce.dto.ProductDTO;
import com.commerce.dto.ProductResponseDTO;
import com.commerce.mapper.ProductMapper;
import com.commerce.service.ProductService;
import com.commerce.util.SecurityUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/products")
@Slf4j
public class AdminProductController {

	private final ProductService productService;
	private final ProductMapper productMapper;
	private final SecurityUtil securityUtil;

	@GetMapping
	public String productManagement(@RequestParam(defaultValue = "0") int page, Model model) {
		int size = 20; // 페이지 사이즈
		page = Math.max(0, page);

		Page<Product> all = productService.findAll(PageRequest.of(page, size));
		List<Product> products = all.getContent();

		List<AdminProductListDTO> dtoList = productMapper.toAdminResponseDTO(products);
		model.addAttribute("products", dtoList);
		model.addAttribute("page", all);
		return "admin/products";
	}

	// 상품 등록
	@GetMapping("/new")
	public String registerPage(Model model) {
		model.addAttribute("product", new Product());
		return "admin/product-new";
	}

	@PostMapping("/new")
	public String registerProduct(@Validated @ModelAttribute("product") ProductDTO productDTO, BindingResult bindingResult,
		@RequestParam(value = "mainImage", required = false) MultipartFile mainFile,
		@RequestParam(value = "images", required = false) List<MultipartFile> files) throws
		IOException {
		if (bindingResult.hasErrors()) {
			return "admin/product-new";
		}

		Admin admin = securityUtil.getCurrentAdmin();
		Product product = productMapper.toEntity(productDTO, admin);

		productService.saveProduct(product, mainFile, files);
		return "redirect:/admin/products";
	}

	// 상품 수정
	@GetMapping("/edit/{id}")
	public String editPage(@PathVariable Long id, Model model) {
		Product product = productService.findById(id);
		ProductResponseDTO dto = productMapper.toProductResponseDTO(product);
		model.addAttribute("product", dto);
		model.addAttribute("productId", id);

		return "admin/product-edit";
	}

	@PostMapping("/edit/{id}")
	public String update(@PathVariable Long id, @Validated @ModelAttribute("product") ProductResponseDTO updatedProduct, BindingResult bindingResult,
		@RequestParam(value = "mainImage", required = false) MultipartFile mainFile,
		@RequestParam(value = "images", required = false) List<MultipartFile> files,
		@RequestParam(value = "deleteImageIds", required = false) List<Long> deleteImageIds,
		Model model) throws
		IOException {

		if (bindingResult.hasErrors()) {
			Product product = productService.findById(id);
			ProductResponseDTO original = productMapper.toProductResponseDTO(product);

			updatedProduct.setMainImageUrl(original.getMainImageUrl());
			updatedProduct.setImages(original.getImages());
			model.addAttribute("productId", id);
			return "admin/product-edit";
		}

		productService.updateProduct(id, updatedProduct, deleteImageIds, mainFile, files);

		return "redirect:/admin/products";
	}

	@PostMapping("/featured")
	public String featured(FeaturedUpdateForm form) {
		List<FeaturedItem> items = form.getItems();
		productService.updateFeatured(items);
		return "redirect:/admin/products";
	}

	// 상품 삭제
	@PostMapping("/delete/{id}")
	public String delete(@PathVariable Long id) {

		productService.deleteProduct(id);

		return "redirect:/admin/products";
	}
}
