package com.commerce.common.storage;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.exception.ApiException;

@Component
@Profile(value = "local")
public class LocalFileStorage implements FileStorage{
	@Value("${file.url-path}")
	private String urlPath;

	@Value("${file.dir}")
	private String dir;

	@Override
	public UploadFile storeImage(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new ApiException(GeneralResponseCode.IMAGE_EMPTY);
		}

		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new ApiException(GeneralResponseCode.IMAGE_TYPE_INVALID);
		}

		String originalFilename = file.getOriginalFilename();
		String storeFileName = createStoreFileName(originalFilename);

		file.transferTo(new File(dir + storeFileName));
		return new UploadFile(originalFilename, storeFileName);

	}

	@Override
	public void delete(String storeFileName) {
		File file = new File(getImageUrl(storeFileName));
		if (file.exists()) {
			file.delete();
		}
	}

	@Override
	public String getImageUrl(String storeName) {
		if (storeName.startsWith("/")) {
			return urlPath + storeName.substring(1);
		}
		return urlPath + storeName;
	}

	private String createStoreFileName(String originalFileName) {
		String ext = extractExt(originalFileName);
		String uuid = UUID.randomUUID().toString();
		return uuid + "." + ext;
	}

	private String extractExt(String originalFilename) {
		int pos = originalFilename.lastIndexOf(".") + 1;
		return originalFilename.substring(pos);
	}
}
