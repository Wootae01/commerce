package com.commerce.product.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.dto.GeneralResponse;
import com.commerce.common.dto.PageResponse;
import com.commerce.product.domain.Product;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.dto.ProductDetailDTO;
import com.commerce.product.dto.ProductHomeDTO;
import com.commerce.product.dto.ProductMapper;
import com.commerce.product.dto.ProductSearchRequest;
import com.commerce.product.service.ProductService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductApiController {

	private static final int POPULAR_DAYS = 30;

	private final ProductService productService;
	private final ProductMapper productMapper;

	// 상품 검색 (keyword, minPrice, maxPrice, sortType, salesPeriod)
	@GetMapping
	public ResponseEntity<GeneralResponse<PageResponse<ProductHomeDTO>>> searchProducts(ProductSearchRequest request,
		@RequestParam(defaultValue = "0") @Min(0) int page,
		@RequestParam(defaultValue = "21") @Min(1) @Max(100) int size) {

		Page<ProductHomeDTO> result = productService.searchProducts(request, PageRequest.of(page, size));
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, PageResponse.from(result));
	}

	// 홈 추천 상품
	@GetMapping("/featured")
	public ResponseEntity<GeneralResponse<List<ProductHomeDTO>>> getFeaturedProducts() {
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, productService.findFeaturedProducts());
	}

	// 홈 인기 상품 (최근 30일 판매량 순)
	@GetMapping("/popular")
	public ResponseEntity<GeneralResponse<List<ProductHomeDTO>>> getPopularProducts(
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK,
			productService.findPopularProductHome(POPULAR_DAYS, size));
	}

	// 상품 상세
	@GetMapping("/{id}")
	public ResponseEntity<GeneralResponse<ProductDetailDTO>> getProductDetail(@PathVariable Long id) {
		Product product = productService.findByIdWithImage(id);
		List<ProductOption> options = productService.findOptionsByProductId(id);
		return GeneralResponse.toResponseEntity(GeneralResponseCode.OK,
			productMapper.toProductDetailDTO(product, options));
	}
}
