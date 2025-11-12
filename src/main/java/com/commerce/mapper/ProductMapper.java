package com.commerce.mapper;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.commerce.domain.Image;
import com.commerce.domain.Product;
import com.commerce.dto.AdminProductListDTO;
import com.commerce.dto.ImageResponseDTO;
import com.commerce.dto.ProductDTO;
import com.commerce.dto.ProductResponseDTO;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProductMapper {

	@Value("${file.url-path}")
	private String baseUrl;

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
			product.getImages().stream()
				.filter(Image::isMain)
				.findFirst()
				.map(image -> baseUrl + image.getStoreFileName())
				.orElse(baseUrl + "default.png"));

		return dto;
	}

	public ProductResponseDTO toProductResponseDTO(Product product) {
		ProductResponseDTO dto = new ProductResponseDTO();
		dto.setId(product.getId());
		dto.setName(product.getName());
		dto.setPrice(product.getPrice());
		dto.setStock(product.getStock());
		dto.setCreatedAt(product.getCreatedAt());
		dto.setMainImageUrl(
			product.getImages().stream()
				.filter(Image::isMain)
				.findFirst()
				.map(image -> baseUrl + image.getStoreFileName())
				.orElse(baseUrl + "default.png"));
		dto.setImages(
			product.getImages().stream()
				.filter(image -> image.isMain() == false)
				.map(image -> new ImageResponseDTO(image.getId(), baseUrl + image.getStoreFileName(), image.isMain()))
				.toList()
		);
		return dto;
	}
}
