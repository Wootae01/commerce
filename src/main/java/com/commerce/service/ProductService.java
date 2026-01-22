package com.commerce.service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.commerce.dto.*;
import com.commerce.util.ProductImageUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.commerce.domain.Image;
import com.commerce.domain.Product;
import com.commerce.domain.enums.OrderStatus;
import com.commerce.repository.ImageRepository;
import com.commerce.repository.OrderProductRepository;
import com.commerce.repository.ProductJdbcRepository;
import com.commerce.repository.ProductRepository;
import com.commerce.storage.FileStorage;
import com.commerce.storage.UploadFile;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ImageRepository imageRepository;
    private final FileStorage fileStorage;
    private final ProductJdbcRepository productJdbcRepository;
    private final OrderProductRepository orderProductRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private final ProductImageUtil imageUtil;

    @Value("${app.image.default-path}")
    private String defaultImagePath;

    private static final String FEATURED_CACHE_KEY = "commerce:product:home:featured";
    private final Duration FEATURED_TTL = Duration.ofHours(24);

    // 관리자가 등록한 홈 product 반환
    public List<ProductHomeDTO> findFeaturedProducts() {

        List<ProductHomeDTO> dtoList = readFeaturedFromCache(FEATURED_CACHE_KEY);

        // 캐시 미스인 경우 db 조회
        if (dtoList == null || dtoList.isEmpty()) {
            dtoList = productRepository.findHomeProductsByFeatured();
            writeToCache(FEATURED_CACHE_KEY, dtoList, FEATURED_TTL);
        }

        for (ProductHomeDTO dto : dtoList) {
            dto.setMainImageUrl(imageUtil.getImageUrl(dto.getMainImageUrl()));
        }

        return dtoList;
    }
    // 캐시에서 featured 읽기
    private List<ProductHomeDTO> readFeaturedFromCache(String cacheKey) {
        String serialized = redisTemplate.opsForValue().get(cacheKey);
        if (serialized == null) return null;

        try {
            return objectMapper.readValue(serialized, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("캐시 역직렬화 실패. cacheKey={}, 캐시 삭제", cacheKey, e);
            redisTemplate.delete(cacheKey);
            return null;
        }
    }

    // 캐시 쓰기
    private void writeToCache(String cacheKey, List<ProductHomeDTO> dtoList, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(dtoList), ttl);
        } catch (JsonProcessingException ex) {
            log.warn("fallback 캐시 저장 실패. cacheKey={}", cacheKey, ex);
        }
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

        List<Long> productIds = popularProducts.stream().map(ProductSoldRow::productId).toList();

        // dto 가져오기
        List<ProductHomeDTO> dtos = productRepository.findHomeProductsByIds(productIds);

        // 판매량 순 정렬
        Map<Long, Long> order = new HashMap<>();
        for (ProductSoldRow row : popularProducts) {
            order.put(row.productId(), row.quantity());
        }

        List<ProductHomeDTO> sorted = dtos.stream().sorted(Comparator.comparing(dto -> order.getOrDefault(dto.getId(), Long.MAX_VALUE))).toList();
        for (ProductHomeDTO dto : sorted) {
            dto.setMainImageUrl(imageUtil.getImageUrl(dto.getMainImageUrl()));
        }

        return dtos;
    }

    // 홈에 보여줄 상품 업데이트 featured update
    @Transactional
    public void updateFeatured(List<FeaturedItem> items) {
        productJdbcRepository.updateFeaturedBatch(items);

        // 트랜잭션 커밋 후 캐시 무효화
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        redisTemplate.delete(FEATURED_CACHE_KEY);
                    }
                }
        );
    }

    // 모든 상품 반환
    public Page<ProductHomeDTO> findHomeProducts(Pageable pageable) {
        Page<ProductHomeDTO> page = productRepository.findHomeProducts(pageable);

        return page.map(dto -> {
            dto.setMainImageUrl(imageUtil.getImageUrl(dto.getMainImageUrl()));
            return dto;
        });

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

    public Product findByIdWithImage(Long id) {
        return productRepository.findByIdWithImage(id)
                .orElseThrow(() -> new NoSuchElementException("해당 상품을 찾을 수 없습니다."));
    }

    // 모든 상품 검색

    public Page<AdminProductListDTO> findAdminProductListDTO(Pageable pageable) {
        Page<AdminProductListDTO> page = productRepository.findAdminProductListDTO(pageable);
        List<AdminProductListDTO> content = page.getContent();

        // 이미지 url 변경. s3, 로컬에 맞게
        // default 이미지 경로면 그대로 유지
        for (AdminProductListDTO dto : content) {

            if (!dto.getMainImageUrl().equals(defaultImagePath)) {
                String imageUrl = fileStorage.getImageUrl(dto.getMainImageUrl());
                dto.setMainImageUrl(imageUrl);
            }
        }
        return page;
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
