package com.university.grade.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Component
public class GradeReleasePolicyCache {
    private static final Logger logger = LoggerFactory.getLogger(GradeReleasePolicyCache.class);
    private static final String KEY_PREFIX = "grade:release:";
    private static final Duration BASE_TTL = Duration.ofHours(1);
    private static final long JITTER_MAX_SECONDS = 300;

    private final RedisTemplate<String, String> redisTemplate;

    public GradeReleasePolicyCache(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private Duration getEffectiveTtl() {
        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, JITTER_MAX_SECONDS + 1);
        return BASE_TTL.plusSeconds(jitterSeconds);
    }

    public Optional<Boolean> get(String semester) {
        String key = buildKey(semester);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                if ("true".equals(value) || "false".equals(value)) {
                    Boolean isReleased = "true".equals(value);
                    logger.debug("[Redis][GradeReleasePolicy] Cache HIT: semester={}, isReleased={}", semester,
                            isReleased);
                    return Optional.of(isReleased);
                } else {
                    redisTemplate.delete(key);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("[Redis][GradeReleasePolicy] Read failure, falling back to DB: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Boolean getOrLoad(String semester, Supplier<Boolean> loader) {
        Optional<Boolean> cached = get(semester);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Load from source
        Boolean result = loader.get();
        if (result != null) {
            put(semester, result);
        }
        return result;
    }

    public void put(String semester, Boolean isReleased) {
        String key = buildKey(semester);
        try {
            Duration effectiveTtl = getEffectiveTtl();
            redisTemplate.opsForValue().set(key, String.valueOf(isReleased), effectiveTtl);
            logger.debug("[Redis][GradeReleasePolicy] Cached: semester={}, isReleased={}, ttl={}s",
                    key.substring(key.lastIndexOf(':') + 1), isReleased, effectiveTtl.getSeconds());
        } catch (Exception e) {
            logger.warn("[Redis][GradeReleasePolicy] Write failure (non-critical): {}", e.getMessage());
        }
    }

    public void evict(String semester) {
        String key = buildKey(semester);
        try {
            redisTemplate.delete(key);
            logger.debug("[Redis][GradeReleasePolicy] Evicted: semester={}", semester);
        } catch (Exception e) {
            logger.warn("[Redis][GradeReleasePolicy] Eviction failure (non-critical): {}", e.getMessage());
        }
    }

    private String buildKey(String semester) {
        return KEY_PREFIX + semester;
    }
}