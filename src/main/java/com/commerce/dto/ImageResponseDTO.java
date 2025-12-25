package com.commerce.dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImageResponseDTO {
	private Long id;
	private String url;
	private boolean isMain;

	public ImageResponseDTO(Long id, String url, boolean isMain) {
		this.id = id;
		this.url = url;
		this.isMain = isMain;
	}
}
