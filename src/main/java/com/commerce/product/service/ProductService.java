package com.commerce.product.service;

import com.commerce.admin.dto.ProductOptionDTO;
import com.commerce.product.domain.Image;
import com.commerce.product.domain.Product;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.ProductSortType;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.dto.*;
import com.commerce.admin.dto.AdminProductListDTO;
import com.commerce.product.repository.ImageRepository;
import com.commerce.order.repository.OrderProductRepository;
import com.commerce.product.repository.ProductJdbcRepository;
import com.commerce.product.repository.ProductOptionRepository;
import com.commerce.product.repository.ProductRepository;
import com.commerce.common.exception.EntityNotFoundException;
import com.commerce.common.storage.FileStorage;
import com.commerce.common.storage.UploadFile;
import com.commerce.common.template.CacheTemplate;
import com.commerce.common.util.ProductImageUtil;
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
import java.util.stream.Collectors;

import static com.commerce.common.support.ProductCachePolicy.*;


@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ImageRepository imageRepository;
    private final ProductJdbcRepository productJdbcRepository;
    private final OrderProductRepository orderProductRepository;
    private final ProductOptionRepository productOptionRepository;

    private final CacheTemplate cacheTemplate;

    private final FileStorage fileStorage;
    private final ProductImageUtil imageUtil;

    @Value("${app.image.default-path}")
    private String defaultImagePath;


   /**
    * 관리자가 등록한 home product 반환
    * */
    public List<ProductHomeDTO> findFeaturedProducts() {

        // 캐시에서 조회
        List<ProductHomeDTO> dtoList = cacheTemplate.execute(
                FEATURED_KEY, FEATURED_LOCK_KEY, FEATURED_LOCK_TTL_MS, FEATURED_TTL,
                new TypeReference<List<ProductHomeDTO>>() {},
                productRepository::findHomeProductsByFeatured);


        // 이미지 url 처리
        for (ProductHomeDTO dto : dtoList) {
            dto.setMainImageUrl(imageUtil.getImageUrl(dto.getMainImageUrl()));
        }

        return dtoList;
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

        String cacheKey = PREFIX_POPULAR_KEY + ":days" + days + ":top" + limit;
        String lockKey = PREFIX_POPULAR_LOCK_KEY + ":days" + days + ":top" + limit;

        // 캐시에서 조회

        List<ProductHomeDTO> dtoList = cacheTemplate.execute(cacheKey, lockKey, POPULAR_LOCK_TTL_MS, POPULAR_TTL,
                new TypeReference<List<ProductHomeDTO>>() {},
                () -> {
                    // 인기 상품 조회
                    List<ProductSoldRow> popularProducts = orderProductRepository.findPopularProducts(
                            statuses, since, PageRequest.of(0, limit));

                    // 상품 id만 뽑고
                    List<Long> productIds = popularProducts.stream().map(ProductSoldRow::productId).toList();
                    if (productIds.isEmpty()) return List.of();

                    Map<Long, Long> quantityMap = new HashMap<>();
                    for (ProductSoldRow row : popularProducts) {
                        quantityMap.put(row.productId(), row.quantity());
                    }
                    // 홈화면 상품 조회 후 판매량 순 정렬
                    return productRepository.findHomeProductsByIds(productIds).stream()
                            .sorted(Comparator.<ProductHomeDTO>comparingLong(
                                    dto -> quantityMap.getOrDefault(dto.getId(), 0L)
                            ).reversed())
                            .toList();
                });

        // 이미지 url 처리
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
                        cacheTemplate.delete(FEATURED_KEY);
                    }
                }
        );
    }

    public Image findImageById(Long imageId) {
        return imageRepository.findById(imageId)
            .orElseThrow();
    }

    // id로 상품 검색
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("해당 상품을 찾을 수 없습니다."));
    }

    public Product findByIdWithImage(Long id) {
        return productRepository.findByIdWithImage(id)
                .orElseThrow(() -> new EntityNotFoundException("해당 상품을 찾을 수 없습니다."));
    }

    public List<ProductOption> findOptionsByProductId(Long id) {
        return productOptionRepository.findByProductId(id);
    }

    public ProductOption findOptionById(Long id) {
        return productOptionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("해당 옵션을 찾을 수 없습니다."));
    }

    public Product findByIdWithOptions(Long id) {
        return productRepository.findByIdWithOptions(id)
                .orElseThrow(() -> new EntityNotFoundException("해당 상품을 찾을 수 없습니다."));
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
            product.setMainImage(mainImage);
        } else {
            Image mainImage = Image.createMainImage(new UploadFile("", defaultImagePath));
            product.setMainImage(mainImage);
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
            .orElseThrow(() -> new EntityNotFoundException("해당 상품을 찾을 수 없습니다."));

        // 대표 이미지 삭제
        Image mainImage = product.getMainImage();
        if (mainImage != null) {
            fileStorage.delete(mainImage.getStoreFileName());
        }

        // 서브 이미지 삭제
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

        Product product = productRepository.findByIdWithOptions(id)
            .orElseThrow(() -> new EntityNotFoundException("해당 상품을 찾을 수 없습니다."));
        product.update(
            updatedProduct.getPrice(),
            updatedProduct.getStock(),
            updatedProduct.getName(),
            updatedProduct.getDescription()
        );

        List<ProductOptionDTO> optionDTOList = updatedProduct.getProductOptionDTOList() == null
            ? List.of()
            : updatedProduct.getProductOptionDTOList().stream()
                .filter(o -> o.getName() != null && !o.getName().isBlank())
                .toList();
        Map<Long, ProductOption> existingOptions = product.getOptions().stream()
            .collect(Collectors.toMap(ProductOption::getId, o -> o));

        Set<Long> incomingIds = new HashSet<>();
        for (ProductOptionDTO dto : optionDTOList) {
            // 기존에 있는 옵션이면 업데이트, 아니면 옵션 추가
            int stock = dto.getStock() != null ? dto.getStock() : 0;
            int additionalPrice = dto.getAdditionalPrice() != null ? dto.getAdditionalPrice() : 0;
            if (dto.getId() != null && existingOptions.containsKey(dto.getId())) {
                existingOptions.get(dto.getId()).update(dto.getName(), stock, additionalPrice);
                incomingIds.add(dto.getId());
            } else {
                product.addOption(new ProductOption(dto.getName(), stock, additionalPrice));
            }
        }
        // DTO에 없는 기존 옵션 삭제
        product.getOptions().removeIf(o -> !incomingIds.contains(o.getId()));

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
            if (file == null || file.isEmpty()) continue;
            uploadFiles.add(fileStorage.storeImage(file));
        }

        // 이미지 객체 생성, 연관관계 설정
        int order = product.getImages().size() + 1;  // 서브 이미지는 1부터 시작
        for (UploadFile uploadFile : uploadFiles) {
            Image image = Image.createSubImage(uploadFile, order++);
            product.addImage(image);
        }
    }

    private void replaceMainImage(MultipartFile mainFile, Product product) throws IOException {
        // 기존 대표 이미지 삭제
        Image oldMainImage = product.getMainImage();
        if (oldMainImage != null) {
            fileStorage.delete(oldMainImage.getStoreFileName());
        }

        // 새 대표 이미지 저장
        UploadFile uploadFile = fileStorage.storeImage(mainFile);
        Image newMainImage = Image.createMainImage(uploadFile);
        product.setMainImage(newMainImage);
    }

    public Page<ProductHomeDTO> searchProducts(ProductSearchRequest request, Pageable pageable) {
        String keyword = request.hasKeyword()  ? request.getKeyword() : null;
        Integer minPrice = request.hasMinPrice() ? request.getMinPrice() : null;
        Integer maxPrice = request.hasMaxPrice() ? request.getMaxPrice() : null;

        Page<ProductHomeDTO> page;
        // 판매량 순 정렬
        if (request.getSortType() == ProductSortType.BEST_SELLING) {
            List<OrderStatus> statuses = List.of(OrderStatus.PAID, OrderStatus.PREPARING,
                    OrderStatus.SHIPPING, OrderStatus.DELIVERED);
            page = productRepository.searchProductBySales(
                    keyword, minPrice, maxPrice, request.getSalesPeriod().getStartDate(),
                    statuses, pageable);

        } else {
            // 일반 정렬
            Pageable sortedPageable = PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    request.getSortType().getSort()
            );
            page = productRepository.searchProducts(keyword, minPrice, maxPrice, sortedPageable);

        }
        // 이미지 url 처리
        return page.map(dto -> {
            dto.setMainImageUrl(imageUtil.getImageUrl(dto.getMainImageUrl()));
            return dto;
        });
    }
}
