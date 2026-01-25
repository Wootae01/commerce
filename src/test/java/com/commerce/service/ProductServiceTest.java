package com.commerce.service;

import com.commerce.config.IntegrationTest;
import com.commerce.dto.ProductHomeDTO;
import com.commerce.repository.ProductRepository;
import com.commerce.support.ProductCachePolicy;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@IntegrationTest
@Slf4j
public class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @MockitoSpyBean
    private ProductRepository productRepository;

    private static final int LOOP_COUNT = 300;

    @Test
    @DisplayName("관리자 설정한 홈 상품 분산락 테스트")
    public void featuredProductLockTest() throws InterruptedException {
        redisTemplate.delete(ProductCachePolicy.FEATURED_KEY); // 캐시 삭제

        ExecutorService executor = getExecutor();
        CountDownLatch readyLatch = new CountDownLatch(LOOP_COUNT);
        CountDownLatch fireLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(LOOP_COUNT);

        for (int i = 0; i < LOOP_COUNT; i++) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    fireLatch.await(); // 모든 스레드가 준비될때까지 대기
                    List<ProductHomeDTO> featuredProducts = productService.findFeaturedProducts();
                } catch (Exception e) {
                    log.info("", e);
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 모든 스레드 준비 후 동시에 발사
        readyLatch.await();
        fireLatch.countDown();
        doneLatch.await();

        Mockito.verify(productRepository, Mockito.times(1))
                .findHomeProductsByFeatured();
    }

    private ExecutorService getExecutor() {
        ExecutorService executor = Executors.newFixedThreadPool(300);
        for (int i = 0; i < 300; i++) {
            // warm up
            executor.submit(() -> {
                sleep(10);
                Math.sin(LocalTime.now().toNanoOfDay());
            });
        }

        return executor;
    }

    private void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
