package com.commerce.service;

import com.commerce.domain.Product;
import com.commerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

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
    public Product save(Product product) {
        return productRepository.save(product);
    }

    // 상품 수정
    public void delete(Long id) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("해당 상품을 찾을 수 없습니다."));
        productRepository.delete(product);
    }

    // 상품 삭제
    public Product update(Long id, Product updatedProduct) {
        Product existProduct = productRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("해당 상품을 찾을 수 없습니다."));


        existProduct.setName(updatedProduct.getName());
        existProduct.setDescription(updatedProduct.getDescription());
        existProduct.setPrice(updatedProduct.getPrice());
        existProduct.setStock(updatedProduct.getStock());

        return productRepository.save(existProduct);
    }

}
