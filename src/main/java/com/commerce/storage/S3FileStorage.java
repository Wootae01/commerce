package com.commerce.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Profile(value = {"dev", "prod"})
@Slf4j
public class S3FileStorage implements FileStorage {

	private final S3Client s3Client;

	@Value("${aws.s3.bucket}")
	private String bucket;

	@Override
	public UploadFile storeImage(MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("빈 파일 입니다.");
		}

		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new IllegalArgumentException("이미지 파일만 업로드 가능");
		}


		String originalFilename = file.getOriginalFilename();
		String ext = extractExt(file.getOriginalFilename());
		String storeFileName = "public/images/" + UUID.randomUUID() + ext;

		// 메타 정보 생성
		PutObjectRequest req = PutObjectRequest.builder()
			.bucket(bucket)
			.key(storeFileName)
			.contentType(contentType)
			.build();

		// s3에 업로드
		s3Client.putObject(req, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

		return new UploadFile(originalFilename, storeFileName);
	}

	// S3 private 이미지에 대한 10분짜리 임시 접근 링크 만들어주는 메서드
	@Override
	public String getImageUrl(String storeName) {

		return "https://" + bucket + ".s3.ap-northeast-2.amazonaws.com/" + storeName;
	}

	@Override
	public void delete(String storeName) {
		s3Client.deleteObject(b -> b.bucket(bucket).key(storeName));
	}

	private String extractExt(String filename) {
		if (filename == null) {
			return "";
		}

		int idx = filename.lastIndexOf(".");

		return idx >= 0 ? filename.substring(idx) : "";
	}
}
