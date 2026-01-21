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
    private static final String KEY_PREFIX = "v1:grade:summary:";
    private static final Duration BASE_TTL = Duration.ofSeconds(3600);
    private static final long JITTER_MAX_SECONDS = 300;
    private static final Duration LOCK_TTL = Duration.ofSeconds(15);
    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_MS = {20, 40, 80};

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;

    public GradeSummaryCache(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.circuitBreaker = new CircuitBreaker(5, 60000);
    }

    private Duration getEffectiveTtl() {
        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, JITTER_MAX_SECONDS + 1);
        return BASE_TTL.plusSeconds(jitterSeconds);
    }

    public Optional<GradeSummaryResponse> get(Long studentId, String semester) {
        if (circuitBreaker.isOpen()) {
            logger.debug("[Redis][GradeSummary] Circuit breaker open, skipping Redis");
            return Optional.empty();
        }

        String key = buildKey(studentId, semester);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                try {
                    GradeSummaryResponse response = objectMapper.readValue(value, GradeSummaryResponse.class);
                    circuitBreaker.recordSuccess();
                    logger.debug("[Redis][GradeSummary] Cache HIT: semester={}", semester);
                    return Optional.of(response);
                } catch (JsonProcessingException e) {
                    logger.warn("[Redis][GradeSummary] Deserialization failure, evicting corrupted key: {}", e.getMessage());
                    evict(studentId, semester);
                    return Optional.empty();
                }
            }
            logger.debug("[Redis][GradeSummary] Cache MISS: semester={}", semester);
            return Optional.empty();
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            logger.warn("[Redis][GradeSummary] Read failure, falling back to DB: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public GradeSummaryResponse getOrLoad(Long studentId, String semester, Supplier<GradeSummaryResponse> loader) {
        if (circuitBreaker.isOpen()) {
            logger.debug("[Redis][GradeSummary] Circuit breaker open, using loader directly");
            return loader.get();
        }

        String key = buildKey(studentId, semester);
        String lockKey = "lock:" + key;

        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                try {
                    GradeSummaryResponse response = objectMapper.readValue(value, GradeSummaryResponse.class);
                    circuitBreaker.recordSuccess();
                    logger.debug("[Redis][GradeSummary] Cache HIT: semester={}", semester);
                    return response;
                } catch (JsonProcessingException e) {
                    logger.warn("[Redis][GradeSummary] Deserialization failure, evicting corrupted key: {}", e.getMessage());
                    evict(studentId, semester);
                }
            }

            RedisLock lock = new RedisLock(redisTemplate, lockKey, LOCK_TTL);
            if (lock.tryLock()) {
                try {
                    value = redisTemplate.opsForValue().get(key);
                    if (value != null) {
                        try {
                            GradeSummaryResponse response = objectMapper.readValue(value, GradeSummaryResponse.class);
                            circuitBreaker.recordSuccess();
                            logger.debug("[Redis][GradeSummary] Cache HIT after lock: semester={}", semester);
                            return response;
                        } catch (JsonProcessingException e) {
                            logger.warn("[Redis][GradeSummary] Deserialization failure after lock, evicting: {}", e.getMessage());
                            evict(studentId, semester);
                        }
                    }

                    logger.debug("[Redis][GradeSummary] Cache MISS, loading from DB: semester={}", semester);
                    GradeSummaryResponse response = loader.get();
                    putInternal(key, response);
                    return response;
                } finally {
                    lock.unlock();
                }
            } else {
                for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                    long backoffMs = BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)];
                    long jitter = ThreadLocalRandom.current().nextLong(-10, 31);
                    long sleepMs = Math.max(0, backoffMs + jitter);
                    
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    value = redisTemplate.opsForValue().get(key);
                    if (value != null) {
                        try {
                            GradeSummaryResponse response = objectMapper.readValue(value, GradeSummaryResponse.class);
                            circuitBreaker.recordSuccess();
                            logger.debug("[Redis][GradeSummary] Cache HIT after retry {}: semester={}", attempt + 1, semester);
                            return response;
                        } catch (JsonProcessingException e) {
                            logger.warn("[Redis][GradeSummary] Deserialization failure after retry, evicting: {}", e.getMessage());
                            evict(studentId, semester);
                        }
                    }
                }

                logger.warn("[Redis][GradeSummary] All retries exhausted, using loader as fallback: semester={}", semester);
                return loader.get();
            }
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            logger.warn("[Redis][GradeSummary] Error in getOrLoad, using loader: {}", e.getMessage());
            return loader.get();
        }
    }

    public void put(Long studentId, String semester, GradeSummaryResponse summary) {
        if (circuitBreaker.isOpen()) {
            logger.debug("[Redis][GradeSummary] Circuit breaker open, skipping cache write");
            return;
        }

        String key = buildKey(studentId, semester);
        putInternal(key, summary);
    }

    private void putInternal(String key, GradeSummaryResponse summary) {
        try {
            String value = objectMapper.writeValueAsString(summary);
            Duration effectiveTtl = getEffectiveTtl();
            redisTemplate.opsForValue().set(key, value, effectiveTtl);
            circuitBreaker.recordSuccess();
            logger.debug("[Redis][GradeSummary] Cached: semester={}, ttl={}s", 
                    key.substring(key.lastIndexOf(':') + 1), effectiveTtl.getSeconds());
        } catch (JsonProcessingException e) {
            circuitBreaker.recordFailure();
            logger.error("[Redis][GradeSummary] Serialization failure: {}", e.getMessage());
        } catch (Exception e) {
            circuitBreaker.recordFailure();
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
