package com.commerce.service;

import com.commerce.domain.Image;
import com.commerce.domain.Product;
import com.commerce.domain.enums.OrderStatus;
import com.commerce.dto.*;
import com.commerce.repository.ImageRepository;
import com.commerce.repository.OrderProductRepository;
import com.commerce.repository.ProductJdbcRepository;
import com.commerce.repository.ProductRepository;
import com.commerce.storage.FileStorage;
import com.commerce.storage.UploadFile;
import com.commerce.support.RedisCacheClient;
import com.commerce.support.RedisDistributedLockProvider;
import com.commerce.util.ProductImageUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.commerce.support.ProductCachePolicy.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ImageRepository imageRepository;
    private final ProductJdbcRepository productJdbcRepository;
    private final OrderProductRepository orderProductRepository;

    private final RedisCacheClient redisCacheClient;
    private final RedisDistributedLockProvider distributedLockProvider;

    private final FileStorage fileStorage;
    private final ProductImageUtil imageUtil;

    @Value("${app.image.default-path}")
    private String defaultImagePath;


   /**
    * 관리자가 등록한 home product 반환
    * */
    public List<ProductHomeDTO> findFeaturedProducts() {

        List<ProductHomeDTO> dtoList = null;

        // 1. 캐시 조회
        Optional<List<ProductHomeDTO>> optional = redisCacheClient.get(FEATURED_KEY, new TypeReference<>() {});

        // 2. 캐시 미스인 경우 db 조회
        if (optional.isEmpty()) {

            for (int i = 0; i < MAX_RETRY; i++) {
                String token = distributedLockProvider.tryLock(FEATURED_LOCK_KEY, FEATURED_LOCK_TTL_MS);

                // 락 가져오기 실패 시 재시도
                if (token == null) {
                    long jitter = ThreadLocalRandom.current().nextLong(RETRY_JITTER_MS);
                    sleep(jitter);
                    continue;
                }
                // 락 획득한 경우 캐시 다시 확인
                try {
                    Optional<List<ProductHomeDTO>> again =
                            redisCacheClient.get(FEATURED_KEY, new TypeReference<>() {});
                    if (again.isPresent()) {
                        dtoList = again.get();
                        break;
                    }
                    dtoList = productRepository.findHomeProductsByFeatured();

                    // cache penetration 방지 (null/empty 는 짧게)
                    Duration ttl;
                    if (dtoList == null || dtoList.isEmpty()) {
                        dtoList = Collections.emptyList();
                        ttl = NULL_TTL;
                    } else {
                        ttl = FEATURED_TTL;
                    }
                    redisCacheClient.set(FEATURED_KEY, dtoList, ttl);
                    break;
                } finally {
                    // 락 해제
                    distributedLockProvider.unlock(FEATURED_LOCK_KEY, token);
                }
            }



        } else {
            dtoList = optional.get();
        }

        // MAX_RETRY 회 모두 락 획득에 실패하면 빈 리스트 반환
        if (dtoList == null) {
            log.warn("featured cache lock contention: failed to acquire lock (retries={}, key={})",
                    MAX_RETRY, FEATURED_LOCK_KEY);
            dtoList = Collections.emptyList();
        }

        // 3. 이미지 url 처리
        for (ProductHomeDTO dto : dtoList) {
            dto.setMainImageUrl(imageUtil.getImageUrl(dto.getMainImageUrl()));
        }

        return dtoList;
    }

    private static void sleep(long jitter) {
        try {
            TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 인기 상품 찾기 판매량 기준
     * @param days 최근 며칠간의 판매량인지
     * @param limit 상품 제한 개수
     * @return 인기 상품
     */
    public List<ProductHomeDTO> findPopularProductHome(int days, int limit) {

        List<OrderStatus> statuses = List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPING,
            OrderStatus.DELIVERED);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        List<ProductHomeDTO> dtoList = null;
        String cacheKey = PREFIX_POPULAR_KEY + ":days" + days + ":top" + limit;
        String lockKey = PREFIX_POPULAR_LOCK_KEY + ":days" + days + ":top" + limit;

        // 1. 캐시 조회
        Optional<List<ProductHomeDTO>> optional = redisCacheClient.get(cacheKey, new TypeReference<>() {});

        // 2. 캐시 미스인 경우
        if (optional.isEmpty()) {
            // 락 획득
            for (int i = 0; i < MAX_RETRY; i++) {

                String token = distributedLockProvider.tryLock(lockKey, POPULAR_LOCK_TTL_MS);
                if (token == null) {
                    long jitter = ThreadLocalRandom.current().nextLong(RETRY_JITTER_MS);
                    sleep(jitter);
                    continue;
                }
                // 락 획득한 경우 캐시 다시 확인
                try {
                    Optional<List<ProductHomeDTO>> again = redisCacheClient.get(cacheKey, new TypeReference<>() {});

                    // 캐시 재조회 시 존재하면
                    if (again.isPresent()) {
                        dtoList = again.get();
                        break;
                    }

                    // 존재하지 않으면 db 조회
                    // 주문 많은 상품 id 찾기
                    List<ProductSoldRow> popularProducts = orderProductRepository.findPopularProducts(statuses, since,
                            PageRequest.of(0, limit));
                    List<Long> productIds = popularProducts.stream().map(ProductSoldRow::productId).toList();
                    if (productIds.isEmpty()) {
                        dtoList = List.of();
                        redisCacheClient.set(cacheKey, dtoList, NULL_TTL);
                        break;
                    }

                    dtoList = productRepository.findHomeProductsByIds(productIds);

                    // 판매량 순 정렬
                    Map<Long, Long> quantityMap = new HashMap<>();
                    for (ProductSoldRow row : popularProducts) {
                        quantityMap.put(row.productId(), row.quantity());
                    }
                    dtoList = dtoList.stream()
                            .sorted(Comparator.<ProductHomeDTO>comparingLong(
                                    dto -> quantityMap.getOrDefault(dto.getId(), 0L)
                            ).reversed())
                            .toList();

                    // 캐시 설정
                    redisCacheClient.set(cacheKey, dtoList, POPULAR_TTL);
                    break;
                } finally {
                    distributedLockProvider.unlock(lockKey, token);
                }
            }

        } else {
            dtoList = optional.get();
        }

        // 락 획득 못한 경우 빈 리스트 반환
        if (dtoList == null) {
            log.warn("popular cache lock contention: failed to acquire lock (retries={}, key={})",
                    MAX_RETRY, lockKey);
            dtoList = Collections.emptyList();
        }
        // 이미지 url 설정
        for (ProductHomeDTO dto : dtoList) {
            dto.setMainImageUrl(imageUtil.getImageUrl(dto.getMainImageUrl()));
        }

        return dtoList;
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
                        redisCacheClient.delete(FEATURED_KEY);
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
    @Transactional
    public void saveProduct(Product product, MultipartFile mainFile, List<MultipartFile> files) throws IOException {

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

        productRepository.save(product);
    }

    // 상품 삭제
    @Transactional
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
    @Transactional
    public void updateProduct(Long id, ProductResponseDTO updatedProduct, List<Long> deleteImageIds,
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
        productRepository.save(product);
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
