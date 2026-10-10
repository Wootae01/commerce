package com.commerce.product.service;

import com.commerce.admin.dto.ProductOptionDTO;
import com.commerce.product.domain.Image;
import com.commerce.product.domain.Product;
import com.commerce.common.enums.OrderStatus;
import com.commerce.common.enums.ProductSortType;
import com.commerce.product.domain.ProductOption;
import com.commerce.product.dto.*;
import com.commerce.admin.dto.AdminProductListDTO;
import com.commerce.order.repository.OrderProductRepository;
import com.commerce.product.repository.ProductJdbcRepository;
import com.commerce.product.repository.ProductOptionRepository;
import com.commerce.product.repository.ProductRepository;
import com.commerce.common.code.GeneralResponseCode;
import com.commerce.common.exception.ApiException;
import com.commerce.common.storage.FileStorage;
import com.commerce.common.storage.UploadFile;
import com.commerce.common.template.CacheTemplate;
import com.commerce.common.util.ProductImageUtil;
import com.commerce.common.util.SecurityUtil;
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
        evictFeaturedCacheAfterCommit();
    }

    // 트랜잭션 커밋 후 홈 노출 상품 캐시 무효화
    private void evictFeaturedCacheAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cacheTemplate.delete(FEATURED_KEY);
                    }
                }
        );
    }

    // id로 상품 검색. 삭제된 상품은 없는 상품으로 본다.
    public Product findById(Long id) {
        return productRepository.findById(id)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ApiException(GeneralResponseCode.PRODUCT_NOT_FOUND));
    }

    public Product findByIdWithImage(Long id) {
        return productRepository.findByIdWithImage(id)
                .orElseThrow(() -> new ApiException(GeneralResponseCode.PRODUCT_NOT_FOUND));
    }

    public List<ProductOption> findOptionsByProductId(Long id) {
        return productOptionRepository.findByProductId(id);
    }

    public ProductOption findOptionById(Long id) {
        return productOptionRepository.findById(id)
                .filter(o -> !o.isDeleted())
                .orElseThrow(() -> new ApiException(GeneralResponseCode.PRODUCT_OPTION_NOT_FOUND));
    }

    /**
     * 장바구니 담기, 주문에 사용할 옵션을 찾는다. 재고와 추가 금액이 옵션에 있으므로 모든 상품은 옵션을 골라야 한다.
     */
    public ProductOption resolveOption(Long productId, Long optionId) {
        if (optionId == null) {
            throw new ApiException(GeneralResponseCode.PRODUCT_OPTION_REQUIRED);
        }

        ProductOption option = findOptionById(optionId);
        // 다른 상품의 옵션이면 가격과 재고가 그 옵션 기준으로 계산되므로 막는다.
        if (!option.getProduct().getId().equals(productId)) {
            throw new ApiException(GeneralResponseCode.PRODUCT_OPTION_MISMATCH);
        }
        return option;
    }

    public Product findByIdWithOptions(Long id) {
        return productRepository.findByIdWithOptions(id)
                .orElseThrow(() -> new ApiException(GeneralResponseCode.PRODUCT_NOT_FOUND));
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

    // 상품 삭제. 지난 주문이 상품·옵션·이미지를 참조하므로 행과 이미지 파일은 남기고 soft delete한다.
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findById(id);

        product.softDelete(SecurityUtil.getCurrentUsername());

        // 홈 노출 상품이면 캐시에 남지 않도록 커밋 후 무효화
        if (product.isFeatured()) {
            evictFeaturedCacheAfterCommit();
        }
    }

    // 상품 수정
    @Transactional
    public void updateProduct(Long id, ProductResponseDTO updatedProduct, List<Long> deleteImageIds,
                              MultipartFile mainFile, List<MultipartFile> files) throws IOException {

        Product product = findByIdWithOptions(id);
        product.update(
            updatedProduct.getPrice(),
            updatedProduct.getName(),
            updatedProduct.getDescription()
        );

        List<ProductOptionDTO> optionDTOList = updatedProduct.getProductOptionDTOList() == null
            ? List.of()
            : updatedProduct.getProductOptionDTOList().stream()
                .filter(o -> o.getName() != null && !o.getName().isBlank())
                .toList();

        // 재고가 옵션에 있으므로 옵션을 모두 지울 수 없다.
        if (optionDTOList.isEmpty()) {
            throw new ApiException(GeneralResponseCode.PRODUCT_OPTION_REQUIRED);
        }
        updateOptions(product, optionDTOList);

        // 서브 이미지 삭제. 이 상품의 이미지만 지울 수 있다.
        if (deleteImageIds != null && !deleteImageIds.isEmpty()) {
            String deletedBy = SecurityUtil.getCurrentUsername();
            product.getActiveImages().stream()
                .filter(image -> deleteImageIds.contains(image.getId()))
                .forEach(image -> image.softDelete(deletedBy));
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

    // 입력한 옵션으로 갱신한다. 기존 옵션은 수정하고, 새 옵션은 추가하고, 빠진 옵션은 지운다.
    private void updateOptions(Product product, List<ProductOptionDTO> optionDTOList) {
        Map<Long, ProductOption> existingOptions = product.getActiveOptions().stream()
            .collect(Collectors.toMap(ProductOption::getId, o -> o));

        Set<Long> incomingIds = new HashSet<>();
        for (ProductOptionDTO dto : optionDTOList) {
            // 기존에 있는 옵션이면 업데이트, 아니면 옵션 추가
            if (dto.getId() != null && existingOptions.containsKey(dto.getId())) {
                int stock = dto.getStock() != null ? dto.getStock() : 0;
                int additionalPrice = dto.getAdditionalPrice() != null ? dto.getAdditionalPrice() : 0;
                existingOptions.get(dto.getId()).update(dto.getName(), stock, additionalPrice);
                incomingIds.add(dto.getId());
            } else {
                product.addOption(ProductOption.createOption(dto.getName(), dto.getStock(), dto.getAdditionalPrice()));
            }
        }
        // DTO에 없는 기존 옵션 삭제. 주문·장바구니가 참조하므로 soft delete한다.
        String deletedBy = SecurityUtil.getCurrentUsername();
        existingOptions.values().stream()
            .filter(o -> !incomingIds.contains(o.getId()))
            .forEach(o -> o.softDelete(deletedBy));
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
        // 기존 대표 이미지 삭제. 지난 주문 내역이 이미지를 보여줄 수 있도록 파일은 남긴다.
        Image oldMainImage = product.getMainImage();
        if (oldMainImage != null) {
            oldMainImage.softDelete(SecurityUtil.getCurrentUsername());
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
