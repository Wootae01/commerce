package com.commerce.mapper;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.commerce.domain.Image;
import com.commerce.domain.Product;
import com.commerce.dto.AdminProductListDTO;
import com.commerce.dto.ProductDetailDTO;
import com.commerce.dto.ProductHomeDTO;
import com.commerce.dto.ImageResponseDTO;
import com.commerce.dto.ProductDTO;
import com.commerce.dto.ProductResponseDTO;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductMapper {

	@Value("${file.url-path}")
	private String baseUrl;
	private final ProductImageUtil productImageUtil;

	public Product toEntity(ProductDTO dto) {
		Product product = new Product(dto.getPrice(), dto.getName(), dto.getStock(), dto.getDescription());
		return product;
	}

	public ProductDTO toDTO(Product product) {
		ProductDTO dto = new ProductDTO();
		dto.setName(product.getName());
		dto.setPrice(product.getPrice());
		dto.setStock(product.getStock());
		dto.setDescription(product.getDescription());
		return dto;
	}

	public List<AdminProductListDTO> toAdminResponseDTO(List<Product> products) {
		List<AdminProductListDTO> result = new ArrayList<>();

		for (Product product : products) {
			result.add(toAdminResponseDTO(product));
		}
		return result;
	}

	public AdminProductListDTO toAdminResponseDTO(Product product) {
		AdminProductListDTO dto = new AdminProductListDTO();
		dto.setId(product.getId());
		dto.setName(product.getName());
		dto.setPrice(product.getPrice());
		dto.setStock(product.getStock());
		dto.setCreatedAt(product.getCreatedAt());
		dto.setMainImageUrl(
			productImageUtil.getMainImageUrl(product));

		return dto;
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
				.filter(image -> image.isMain() == false)
				.map(image -> new ImageResponseDTO(image.getId(), baseUrl + image.getStoreFileName(), image.isMain()))
				.toList()
		);
		return dto;
	}

	public List<ProductHomeDTO> toHomeProductDTO(List<Product> products) {
		List<ProductHomeDTO> result = new ArrayList<>();
		for (Product product : products) {
			result.add(toHomeProductDTO(product));
		}
		return result;
	}
	public ProductHomeDTO toHomeProductDTO(Product product) {
		return new ProductHomeDTO(
			product.getId(), productImageUtil.getMainImageUrl(product), product.getName(), product.getPrice()
		);
	}

	public ProductDetailDTO toProductDetailDTO(Product product) {
		String mainImageUrl = productImageUtil.getMainImageUrl(product);
		List<String> images = productImageUtil.getSubImagesUrl(product);

		return new ProductDetailDTO(
			product.getId(), product.getPrice(), product.getName(), mainImageUrl, images, product.getDescription());
	}
}
