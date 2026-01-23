package com.commerce.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheService {
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // 캐시 읽기
    public <T> Optional<T> get(String cacheKey, TypeReference<T> typeRef) {
        String serialized = redisTemplate.opsForValue().get(cacheKey);
        if (serialized == null) return Optional.empty();

        try {
            return Optional.of(objectMapper.readValue(serialized, typeRef));
        } catch (JsonProcessingException e) {
            log.warn("캐시 역직렬화 실패. cacheKey={}, 캐시 삭제", cacheKey, e);
            redisTemplate.delete(cacheKey);
            return Optional.empty();
        }
    }

    // 캐시 쓰기
    public void set(String cacheKey, Object value, Duration ttl) {
        try {
            Duration jitterTtl = jitterTtl(ttl);
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(value), jitterTtl);
        } catch (JsonProcessingException ex) {
            log.warn("fallback 캐시 저장 실패. cacheKey={}", cacheKey, ex);
        }
    }

    // apply jitter
    public Duration jitterTtl(Duration baseTtl) {
        long baseMs = baseTtl.toMillis();
        long rangeMs = (long) (baseMs * 0.10);
        rangeMs = Math.min(rangeMs, Duration.ofMinutes(10).toMillis());


        long extraMs = ThreadLocalRandom.current().nextLong(0, rangeMs + 1); // 0 ~ range
        long resultMs = baseMs + extraMs;

        return Duration.ofMillis(Math.max(1_000, resultMs)); // 최소 1초
    }
}
