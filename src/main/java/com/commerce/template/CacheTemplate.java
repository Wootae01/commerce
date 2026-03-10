package com.commerce.template;

import com.commerce.support.RedisCacheClient;
import com.commerce.support.RedisDistributedLockProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.commerce.support.ProductCachePolicy.*;

@Slf4j
@RequiredArgsConstructor
@Component
public class CacheTemplate {

    private final RedisCacheClient redisCacheClient;
    private final RedisDistributedLockProvider distributedLockProvider;

    public <T> List<T> execute(String cacheKey, String lockKey, long lockTtlMs,
                               Duration ttl, TypeReference<List<T>> typeRef,
                               Supplier<List<T>> loader) {

        List<T> result = null;

        // 1. 캐시 조회
        Optional<List<T>> optional = redisCacheClient.get(cacheKey, typeRef);

        // 2. 캐시 미스인 경우 db 조회
        if (optional.isEmpty()) {

            for (int i = 0; i < MAX_RETRY; i++) {
                String token = distributedLockProvider.tryLock(lockKey, lockTtlMs);

                // 락 가져오기 실패 시 재시도
                if (token == null) {
                    long jitter = ThreadLocalRandom.current().nextLong(RETRY_JITTER_MS);
                    sleep(jitter);
                    continue;
                }
                // 락 획득한 경우 캐시 다시 확인
                try {
                    Optional<List<T>> again =
                            redisCacheClient.get(cacheKey, typeRef);
                    if (again.isPresent()) {
                        result = again.get();
                        break;
                    }
                    result = loader.get();

                    // cache penetration 방지 (null/empty 는 짧게)
                    Duration resolvedTtl;
                    if (result == null || result.isEmpty()) {
                        result = Collections.emptyList();
                        resolvedTtl = NULL_TTL;
                    } else {
                        resolvedTtl = ttl;
                    }
                    redisCacheClient.set(cacheKey, result, resolvedTtl);
                    break;
                } finally {
                    // 락 해제
                    distributedLockProvider.unlock(lockKey, token);
                }
            }



        } else {
            log.info("cache hit key={}", cacheKey);
            result = optional.get();
        }

        // MAX_RETRY 회 모두 락 획득에 실패하면 빈 리스트 반환
        if (result == null) {
            log.warn("cache lock contention: failed to acquire lock (retries={}, key={})",
                    MAX_RETRY, lockKey);
            result = Collections.emptyList();
        }

        return result;
    }

    public void delete(String cacheKey) {
        redisCacheClient.delete(cacheKey);
    }

    private static void sleep(long jitter) {
        try {
            TimeUnit.MILLISECONDS.sleep(RETRY_DELAY_MS + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
