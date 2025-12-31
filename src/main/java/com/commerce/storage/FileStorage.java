package com.commerce.storage;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

	UploadFile storeImage(MultipartFile file) throws IOException;


	void delete(String storeName);

	String getImageUrl(String storeName);
}
