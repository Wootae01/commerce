package com.commerce.product.controller;

import com.commerce.product.domain.Product;
import com.commerce.common.enums.ProductSortType;
import com.commerce.common.enums.SalesPeriod;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.dto.ProductDetailDTO;
import com.commerce.product.dto.ProductHomeDTO;
import com.commerce.product.dto.ProductSearchRequest;
import com.commerce.product.dto.ProductMapper;
import com.commerce.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;

    // 상품 상세
    @GetMapping("/{id}")
    public String getProductDetail(@PathVariable Long id, Model model) {
        Product product = productService.findByIdWithImage(id);
        List<ProductOption> options = productService.findOptionsByProductId(id);
        ProductDetailDTO dto = productMapper.toProductDetailDTO(product, options);

        model.addAttribute("product", dto);
        return "product-detail";
    }

    // 상품 검색
    @GetMapping("/search")
    public String searchProducts(ProductSearchRequest request, Model model,
                                 @RequestParam(defaultValue = "0") int page){
        // 정렬 ( 낮은 가격, 높은 가격 순, 판매량 순(최근 일주일, 한달, 6개월)
        // keyword 검색, 가격 필터 minPrice, maxPrice 사이

        int size = 21;
        Page<ProductHomeDTO> result = productService.searchProducts(request, PageRequest.of(page, size));

        model.addAttribute("products", result.getContent());
        model.addAttribute("page", result);
        model.addAttribute("search", request);
        model.addAttribute("sortTypes", ProductSortType.values());
        model.addAttribute("salesPeriods", SalesPeriod.values());
        return "search-products";

    }
}
