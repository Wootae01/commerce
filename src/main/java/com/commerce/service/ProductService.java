package com.commerce.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.commerce.domain.Image;
import com.commerce.domain.Product;
import com.commerce.domain.enums.OrderStatus;
import com.commerce.dto.FeaturedItem;
import com.commerce.dto.ProductHomeDTO;
import com.commerce.dto.ProductResponseDTO;
import com.commerce.dto.ProductSoldRow;
import com.commerce.repository.ImageRepository;
import com.commerce.repository.OrderProductRepository;
import com.commerce.repository.ProductJdbcRepository;
import com.commerce.repository.ProductRepository;
import com.commerce.storage.FileStorage;
import com.commerce.storage.UploadFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ImageRepository imageRepository;
    private final FileStorage fileStorage;
    private final ProductJdbcRepository productJdbcRepository;
    private final OrderProductRepository orderProductRepository;

    @Value("${app.image.default-path}")
    private String defaultImagePath;

    // 관리자가 등록한 홈 product 반환
    public List<ProductHomeDTO> findFeaturedProducts() {
        List<ProductHomeDTO> dtos = productRepository.findHomeProductsByFeatured();
        setDefaultImageUrl(dtos);
        return dtos;
    }

    // 인기 상품 찾기 판매량 기준
    //  days: 최근 며칠 기준, limit : 상품 개수
    public List<ProductHomeDTO> findPopularProductHome(int days, int limit) {
        List<OrderStatus> statuses = List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPING,
            OrderStatus.DELIVERED);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<ProductSoldRow> popularProducts = orderProductRepository.findPopularProducts(statuses, since,
            PageRequest.of(0, limit));

        if (popularProducts.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = popularProducts.stream().map(p -> p.productId()).toList();

        // dto 가져오기
        List<ProductHomeDTO> dtos = productRepository.findHomeProductsByIds(productIds);

        // 판매량 순 정렬
        Map<Long, Long> order = new HashMap<>();
        for (ProductSoldRow row : popularProducts) {
            order.put(row.productId(), row.quantity());
        }

        dtos.stream().sorted(Comparator.comparing(dto -> order.getOrDefault(dto.getId(), Long.MAX_VALUE)));
        setDefaultImageUrl(dtos);
        return dtos;
    }

    // 홈에 보여줄 상품 업데이트 featured update
    public void updateFeatured(List<FeaturedItem> items) {
        productJdbcRepository.updateFeaturedBatch(items);
    }

    // 모든 상품 반환
    public Page<ProductHomeDTO> findHomeProducts(Pageable pageable) {
        Page<ProductHomeDTO> page = productRepository.findHomeProducts(pageable);

        return page.map(dto -> {
            if (dto.getMainImageUrl() == null || dto.getMainImageUrl().isBlank()) {
                dto.setMainImageUrl(defaultImagePath);
            }

            return dto;
        });

    }

    private void setDefaultImageUrl(List<ProductHomeDTO> homeProducts) {
        for (ProductHomeDTO homeProduct : homeProducts) {
            if (homeProduct.getMainImageUrl() == null || homeProduct.getMainImageUrl().isEmpty()) {
                homeProduct.setMainImageUrl(defaultImagePath);
            }
        }
    }

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

    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAllWithMainImage(pageable);
    }

    // 상품 등록
    public Product saveProduct(Product product, MultipartFile mainFile, List<MultipartFile> files) throws IOException {

        // 대표 이미지 등록
        if (mainFile != null && !mainFile.isEmpty()) {
            UploadFile uploadFile = fileStorage.storeImage(mainFile);

            Image mainImage = Image.createMainImage(uploadFile);
            product.addImage(mainImage);
        } else {
            Image mainImage = Image.createMainImage(new UploadFile("", defaultImagePath));
            product.addImage(mainImage);
        }

        // 서브 이미지 등록
        if (files != null && !files.isEmpty()) {

            // 서브 이미지 저장
            List<UploadFile> uploadFiles = new ArrayList<>();
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) continue;
                uploadFiles.add(fileStorage.storeImage(file));
            }

            // 이미지, 상품 연관관계
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
            fileStorage.delete(image.getStoreFileName());
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
                fileStorage.delete(image.getStoreFileName());
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

        // 여러 이미지 저장
        List<UploadFile> uploadFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            uploadFiles.add(fileStorage.storeImage(file));
        }

        // 이미지 객체 생성, 연관관계 설정
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
                fileStorage.delete(image.getStoreFileName());
                product.getImages().remove(image);
            });

        // 새 대표 이미지 저장
        UploadFile uploadFile = fileStorage.storeImage(mainFile);
        Image mainImage = Image.createMainImage(uploadFile);
        product.addImage(mainImage);
    }

}
