package com.commerce.util;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UploadFile {
	private String uploadFileName;
	private String storeFileName;

	public UploadFile(String uploadFileName, String storeFileName) {
		this.uploadFileName = uploadFileName;
		this.storeFileName = storeFileName;
	}
}
