package com.commerce.service;

import com.commerce.domain.Image;
import com.commerce.domain.Product;
import com.commerce.dto.ProductDTO;
import com.commerce.dto.ProductResponseDTO;
import com.commerce.repository.ImageRepository;
import com.commerce.repository.ProductRepository;
import com.commerce.util.FileUtil;
import com.commerce.util.UploadFile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ImageRepository imageRepository;
    private final FileUtil fileUtil;

    public Image findImageById(Long imageId) {
        return imageRepository.findById(imageId)
            .orElseThrow();
    }

    // id로 상품 검색
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("해당 상품을 찾을 수 없습니다."));
    }

    // 모든 상품 검색
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    // 상품 등록
    public Product saveProduct(Product product, MultipartFile mainFile, List<MultipartFile> files) throws IOException {

        // 대표 이미지 등록
        if (mainFile != null && !mainFile.isEmpty()) {
            UploadFile uploadFile = fileUtil.storeFile(mainFile);

            Image mainImage = Image.createMainImage(uploadFile);
            product.addImage(mainImage);
        }

        // 서브 이미지 등록
        if (files != null && !files.isEmpty()) {
            List<UploadFile> uploadFiles = fileUtil.storeFiles(files);
            int order = 1;
            for (UploadFile uploadFile : uploadFiles) {
                Image image = Image.createSubImage(uploadFile, order++);
                product.addImage(image);
            }
        }

        return productRepository.save(product);
    }

    // 상품 삭제
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("해당 상품을 찾을 수 없습니다."));

        List<Image> images = product.getImages();
        for (Image image : images) {
            fileUtil.deleteFile(image.getStoreFileName());
        }

        productRepository.delete(product);
    }

    // 상품 수정
    public Product updateProduct(Long id, ProductResponseDTO updatedProduct, List<Long> deleteImageIds,
        MultipartFile mainFile, List<MultipartFile> files) throws IOException {

        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("해당 상품을 찾을 수 없습니다."));
        product.update(
            updatedProduct.getPrice(),
            updatedProduct.getStock(),
            updatedProduct.getName(),
            updatedProduct.getDescription()
        );

        // 서브 이미지 삭제
        if (deleteImageIds != null && !deleteImageIds.isEmpty()) {
            for (Long imageId : deleteImageIds) {
                Image image = findImageById(imageId);
                fileUtil.deleteFile(image.getStoreFileName());
                product.getImages().remove(image);
            }
        }

        // 기존 대표 이미지 교체
        if (mainFile != null && !mainFile.isEmpty()) {
            replaceMainImage(mainFile, product);
        }

        // 서브 이미지 추가
        if (files != null && !files.isEmpty()) {
            addExtraImages(files, product);
        }
        return productRepository.save(product);
    }

    private void addExtraImages(List<MultipartFile> files, Product product) throws IOException {
        List<UploadFile> uploadFiles = fileUtil.storeFiles(files);
        int order = product.getImages().size();
        for (UploadFile uploadFile : uploadFiles) {
            Image image = Image.createSubImage(uploadFile, order++);
            product.addImage(image);
        }
    }

    private void replaceMainImage(MultipartFile mainFile, Product product) throws IOException {
        product.getImages().stream()
            .filter(Image::isMain)
            .findFirst()
            .ifPresent(image -> {
                fileUtil.deleteFile(image.getStoreFileName());
                product.getImages().remove(image);
            });

        // 새 대표 이미지 저장
        UploadFile uploadFile = fileUtil.storeFile(mainFile);
        Image mainImage = Image.createMainImage(uploadFile);
        product.addImage(mainImage);
    }

}
