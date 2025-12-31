package com.commerce.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.commerce.storage.UploadFile;

@Component
public class FileUtil {

	@Value("${file.dir}")
	private String fileDir;

	public List<UploadFile> storeFiles(List<MultipartFile> multipartFiles) throws IOException {
		List<UploadFile> result = new ArrayList<>();

		for (MultipartFile multipartFile : multipartFiles) {
			if (!multipartFile.isEmpty()) {
				result.add(storeFile(multipartFile));
			}
		}
		return result;
	}

	public UploadFile storeFile(MultipartFile multipartFile) throws IOException {
		if (multipartFile.isEmpty()) {
			return null;
		}
		String originalFilename = multipartFile.getOriginalFilename();
		String storeFileName = createStoreFileName(originalFilename);

		multipartFile.transferTo(new File(getFullPath(storeFileName)));
		return new UploadFile(originalFilename, storeFileName);

	}

	public void deleteFile(String storeFileName) {
		File file = new File(getFullPath(storeFileName));
		if (file.exists()) {
			file.delete();
		}
	}

	private String createStoreFileName(String originalFileName) {
		String ext = extractExt(originalFileName);
		String uuid = UUID.randomUUID().toString();
		return uuid + "." + ext;
	}

	private String extractExt(String originalFilename) {
		int pos = originalFilename.indexOf(".") + 1;
		return originalFilename.substring(pos);
	}
	private String getFullPath(String fileName) {
		return fileDir + fileName;
	}
}
