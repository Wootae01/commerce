package com.commerce.dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImageResponseDTO {
	private Long id;
	private String url;

	public ImageResponseDTO(Long id, String url) {
		this.id = id;
		this.url = url;
	}
}
