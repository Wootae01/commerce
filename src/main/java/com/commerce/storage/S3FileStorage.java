package com.commerce.storage;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@RequiredArgsConstructor
@Profile(value = {"dev", "prod"})
public class S3FileStorage implements FileStorage {

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;

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
		String storeFileName = "images/" + UUID.randomUUID() + ext;

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

	@Override
	public String getImageUrl(String storeName) {

		GetObjectRequest getObjectRequest = GetObjectRequest.builder()
			.bucket(bucket)
			.key(storeName)
			.build();

		GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
			.signatureDuration(Duration.ofMinutes(10))
			.getObjectRequest(getObjectRequest)
			.build();

		return s3Presigner.presignGetObject(presignRequest).url().toString();
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
