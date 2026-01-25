package com.commerce.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisDistributedLockProvider {

    private final RedisTemplate<String, String> redisTemplate;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "  return redis.call('del', KEYS[1]) " +
                        "else " +
                        "  return 0 " +
                        "end"
        );
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    // 분산 락 가져오기
    public String tryLock(String key, long timeoutMs) {
        String token = java.util.UUID.randomUUID().toString();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, token, timeoutMs, TimeUnit.MILLISECONDS);

        if (Boolean.TRUE.equals(success)) {
            return token;
        } else {
            return null;
        }
    }

    // 락 해제, 소유권 검증
    public boolean unlock(String key, String token) {

        Long res = redisTemplate.execute(
                UNLOCK_SCRIPT,
                List.of(key),
                token
        );
        return res != null && res == 1L;
    }
}
