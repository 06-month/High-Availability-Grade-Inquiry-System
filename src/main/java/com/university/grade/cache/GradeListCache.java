package com.university.grade.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.grade.dto.GradeDetailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Component
public class GradeListCache {
    private static final Logger logger = LoggerFactory.getLogger(GradeListCache.class);
    private static final String KEY_PREFIX = "grade:list:";
    private static final Duration BASE_TTL = Duration.ofHours(1);
    private static final long JITTER_MAX_SECONDS = 300;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public GradeListCache(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private Duration getEffectiveTtl() {
        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, JITTER_MAX_SECONDS + 1);
        return BASE_TTL.plusSeconds(jitterSeconds);
    }

    public Optional<List<GradeDetailResponse>> get(Long studentId, String semester) {
        String key = buildKey(studentId, semester);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                try {
                    List<GradeDetailResponse> response = objectMapper.readValue(value,
                            new TypeReference<List<GradeDetailResponse>>() {
                            });
                    logger.debug("[Redis][GradeList] Cache HIT: semester={}", semester);
                    return Optional.of(response);
                } catch (JsonProcessingException e) {
                    logger.warn("[Redis][GradeList] Deserialization failure: {}", e.getMessage());
                    redisTemplate.delete(key);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("[Redis][GradeList] Read failure, falling back to DB: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<GradeDetailResponse> getOrLoad(Long studentId, String semester,
            Supplier<List<GradeDetailResponse>> loader) {
        Optional<List<GradeDetailResponse>> cached = get(studentId, semester);
        if (cached.isPresent()) {
            return cached.get();
        }

        // Load from source
        List<GradeDetailResponse> response = loader.get();
        if (response != null && !response.isEmpty()) {
            put(studentId, semester, response);
        }
        return response;
    }

    public void put(Long studentId, String semester, List<GradeDetailResponse> gradeList) {
        String key = buildKey(studentId, semester);
        try {
            String value = objectMapper.writeValueAsString(gradeList);
            Duration effectiveTtl = getEffectiveTtl();
            redisTemplate.opsForValue().set(key, value, effectiveTtl);
            logger.debug("[Redis][GradeList] Cached: semester={}, count={}, ttl={}s",
                    key.substring(key.lastIndexOf(':') + 1), gradeList.size(), effectiveTtl.getSeconds());
        } catch (JsonProcessingException e) {
            logger.error("[Redis][GradeList] Serialization failure: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("[Redis][GradeList] Write failure (non-critical): {}", e.getMessage());
        }
    }

    public void evict(Long studentId, String semester) {
        String key = buildKey(studentId, semester);
        try {
            redisTemplate.delete(key);
            logger.debug("[Redis][GradeList] Evicted: semester={}", semester);
        } catch (Exception e) {
            logger.warn("[Redis][GradeList] Eviction failure (non-critical): {}", e.getMessage());
        }
    }

    private String buildKey(Long studentId, String semester) {
        return KEY_PREFIX + studentId + ":" + semester;
    }
}