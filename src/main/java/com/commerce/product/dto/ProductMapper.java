package com.commerce.product.dto;

import java.util.List;

import com.commerce.admin.dto.ProductOptionDTO;
import com.commerce.product.domain.ProductOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.commerce.admin.domain.Admin;
import com.commerce.product.domain.Product;
import com.commerce.common.util.ProductImageUtil;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductMapper {

	@Value("${file.url-path}")
	private String baseUrl;
	private final ProductImageUtil productImageUtil;

	public ProductOption toProductOption(ProductOptionDTO dto) {
        int stock = dto.getStock() != null ? dto.getStock() : 0;
        int additionalPrice = dto.getAdditionalPrice() != null ? dto.getAdditionalPrice() : 0;
        return new ProductOption(dto.getName(), stock, additionalPrice);
	}

	public Product toEntity(ProductDTO dto, Admin admin) {
		List<ProductOptionDTO> optionDTOList = dto.getProductOptionDTOList();

		Product product = new Product(admin, dto.getPrice(), dto.getStock(), dto.getName(), dto.getDescription());
		if (optionDTOList != null) {
			for (ProductOptionDTO optionDTO : optionDTOList) {
				if (optionDTO.getName() != null && !optionDTO.getName().isBlank()) {
					product.addOption(toProductOption(optionDTO));
				}
			}
		}

		return product;
	}

	public ProductResponseDTO toProductResponseDTO(Product product) {
		ProductResponseDTO dto = new ProductResponseDTO();
		dto.setId(product.getId());
		dto.setName(product.getName());
		dto.setPrice(product.getPrice());
		dto.setStock(product.getStock());
		dto.setCreatedAt(product.getCreatedAt());
		dto.setMainImageUrl(productImageUtil.getMainImageUrl(product));
		dto.setImages(
			product.getImages().stream()
				.map(image -> new ImageResponseDTO(image.getId(), baseUrl + image.getStoreFileName()))
				.toList()
		);
		dto.setProductOptionDTOList(
			product.getOptions().stream()
				.map(o -> new ProductOptionDTO(o.getId(), o.getName(), o.getStock(), o.getAdditionalPrice()))
				.toList()
		);
		return dto;
	}


	public ProductDetailDTO toProductDetailDTO(Product product, List<ProductOption> options) {
		String mainImageUrl = productImageUtil.getMainImageUrl(product);
		List<String> images = productImageUtil.getSubImagesUrl(product);
		List<ProductOptionDTO> optionDTOs = options.stream()
			.map(o -> new ProductOptionDTO(o.getId(), o.getName(), o.getStock(), o.getAdditionalPrice()))
			.toList();

		return new ProductDetailDTO(
			product.getId(), product.getPrice(), product.getName(), mainImageUrl, images, product.getDescription(), optionDTOs);
	}


}
