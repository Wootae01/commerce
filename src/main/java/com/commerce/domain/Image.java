package com.commerce.domain;

import com.commerce.util.UploadFile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
public class Image {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "image_id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "product_id")
	private Product product;

	private String uploadFileName; // 사용자가 업로드한 파일 이름
	private String storeFileName;  // 서버에서 저장한 파일 이름

	private boolean isMain;
	private int imgOrder;

	public Image() {}

	@Builder
	private Image(String uploadFileName, String storeFileName, boolean isMain, int imgOrder) {
		this.uploadFileName = uploadFileName;
		this.storeFileName = storeFileName;
		this.isMain = isMain;
		this.imgOrder = imgOrder;
	}

	public static Image createMainImage(UploadFile uploadFile) {
		return Image.builder()
			.uploadFileName(uploadFile.getUploadFileName())
			.storeFileName(uploadFile.getStoreFileName())
			.isMain(true)
			.imgOrder(0)
			.build();
	}

	public static Image createSubImage(UploadFile uploadFile, int order) {
		return Image.builder()
			.uploadFileName(uploadFile.getUploadFileName())
			.storeFileName(uploadFile.getStoreFileName())
			.isMain(false)
			.imgOrder(order)
			.build();
	}

	protected void setProduct(Product product) {
		this.product = product;
	}
}
