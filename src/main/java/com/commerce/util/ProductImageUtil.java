package com.commerce.util;

import java.util.List;

import com.commerce.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.commerce.domain.Image;
import com.commerce.domain.Product;

@Component
@RequiredArgsConstructor
public class ProductImageUtil {
	@Value("${file.url-path}")
	private String baseUrl;

	@Value("${app.image.default-path}")
	private String imageDefaultPath;

	private final FileStorage fileStorage;

	public List<String> getSubImagesUrl(Product product) {
		return product.getImages().stream()
				.filter(image -> !image.isMain())
				.map(image -> {
					if (!image.getStoreFileName().equals(imageDefaultPath)) {
						return fileStorage.getImageUrl(image.getStoreFileName());
					}
					return image.getStoreFileName();
				})
				.toList();
	}

	public String getMainImageUrl(Product product) {
		return product.getImages().stream()
			.filter(Image::isMain)
			.findFirst()
			.map(image -> {
				if (!image.getStoreFileName().equals(imageDefaultPath)) {
					return fileStorage.getImageUrl(image.getStoreFileName());
				}
				return image.getStoreFileName();
			})
			.orElse(imageDefaultPath);
	}

	public String getImageUrl(String url) {
		if (url == null || url.isEmpty()) {
			return imageDefaultPath;
		}

		if (!url.equals(imageDefaultPath)) {
			return fileStorage.getImageUrl(url);
		}
		return url;
	}
}
