package com.university.grade.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.grade.dto.GradeSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Component
public class GradeSummaryCache {
    private static final Logger logger = LoggerFactory.getLogger(GradeSummaryCache.class);
    private static final String KEY_PREFIX = "grade:summary:";
    private static final Duration BASE_TTL = Duration.ofHours(1);
    private static final long JITTER_MAX_SECONDS = 300;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public GradeSummaryCache(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private Duration getEffectiveTtl() {
        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, JITTER_MAX_SECONDS + 1);
        return BASE_TTL.plusSeconds(jitterSeconds);
    }

    public Optional<GradeSummaryResponse> get(Long studentId, String semester) {
        String key = buildKey(studentId, semester);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                try {
                    GradeSummaryResponse response = objectMapper.readValue(value, GradeSummaryResponse.class);
                    logger.debug("[Redis][GradeSummary] Cache HIT: semester={}", semester);
                    return Optional.of(response);
                } catch (JsonProcessingException e) {
                    logger.warn("[Redis][GradeSummary] Deserialization failure: {}", e.getMessage());
                    redisTemplate.delete(key);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("[Redis][GradeSummary] Read failure, falling back to DB: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public GradeSummaryResponse getOrLoad(Long studentId, String semester, Supplier<GradeSummaryResponse> loader) {
        Optional<GradeSummaryResponse> cached = get(studentId, semester);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Load from source
        GradeSummaryResponse response = loader.get();
        if (response != null) {
            put(studentId, semester, response);
        }
        return response;
    }

    public void put(Long studentId, String semester, GradeSummaryResponse summary) {
        String key = buildKey(studentId, semester);
        try {
            String value = objectMapper.writeValueAsString(summary);
            Duration effectiveTtl = getEffectiveTtl();
            redisTemplate.opsForValue().set(key, value, effectiveTtl);
            logger.debug("[Redis][GradeSummary] Cached: semester={}, ttl={}s",
                    key.substring(key.lastIndexOf(':') + 1), effectiveTtl.getSeconds());
        } catch (JsonProcessingException e) {
            logger.error("[Redis][GradeSummary] Serialization failure: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("[Redis][GradeSummary] Write failure (non-critical): {}", e.getMessage());
        }
    }

    public void evict(Long studentId, String semester) {
        String key = buildKey(studentId, semester);
        try {
            redisTemplate.delete(key);
            logger.debug("[Redis][GradeSummary] Evicted: semester={}", semester);
        } catch (Exception e) {
            logger.warn("[Redis][GradeSummary] Eviction failure (non-critical): {}", e.getMessage());
        }
    }

    private String buildKey(Long studentId, String semester) {
        return KEY_PREFIX + studentId + ":" + semester;
    }
}