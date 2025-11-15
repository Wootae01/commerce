package com.commerce.mapper;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.commerce.domain.Image;
import com.commerce.domain.Product;

@Component
public class ProductImageUtil {
	@Value("${file.url-path}")
	private String baseUrl;

	public List<String> getSubImagesUrl(Product product) {
		return product.getImages().stream()
			.filter(image -> image.isMain() == false)
			.map(image -> baseUrl + image.getStoreFileName())
			.toList();
	}

	public String getMainImageUrl(Product product) {
		return product.getImages().stream()
			.filter(Image::isMain)
			.findFirst()
			.map(image -> baseUrl + image.getStoreFileName())
			.orElse(baseUrl + "default.png");
	}
}
