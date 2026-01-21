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
    private static final String KEY_PREFIX = "v1:grade:release:";
    private static final Duration BASE_TTL = Duration.ofSeconds(120);
    private static final long JITTER_MAX_SECONDS = 30;
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final int MAX_RETRIES = 3;
    private static final long[] BACKOFF_MS = {20, 40, 80};

    private final RedisTemplate<String, String> redisTemplate;
    private final CircuitBreaker circuitBreaker;

    public GradeReleasePolicyCache(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.circuitBreaker = new CircuitBreaker(5, 60000);
    }

    private Duration getEffectiveTtl() {
        long jitterSeconds = ThreadLocalRandom.current().nextLong(0, JITTER_MAX_SECONDS + 1);
        return BASE_TTL.plusSeconds(jitterSeconds);
    }

    public Optional<Boolean> get(String semester) {
        if (circuitBreaker.isOpen()) {
            logger.debug("[Redis][GradeReleasePolicy] Circuit breaker open, skipping Redis");
            return Optional.empty();
        }

        String key = buildKey(semester);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                if (!"true".equals(value) && !"false".equals(value)) {
                    logger.warn("[Redis][GradeReleasePolicy] Invalid cached value: {}, treating as MISS", value);
                    evict(semester);
                    return Optional.empty();
                }
                Boolean isReleased = "true".equals(value);
                circuitBreaker.recordSuccess();
                logger.debug("[Redis][GradeReleasePolicy] Cache HIT: semester={}, isReleased={}", semester, isReleased);
                return Optional.of(isReleased);
            }
            logger.debug("[Redis][GradeReleasePolicy] Cache MISS: semester={}", semester);
            return Optional.empty();
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            logger.warn("[Redis][GradeReleasePolicy] Read failure, falling back to DB: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Boolean getOrLoad(String semester, Supplier<Boolean> loader) {
        if (circuitBreaker.isOpen()) {
            logger.debug("[Redis][GradeReleasePolicy] Circuit breaker open, using loader directly");
            return loader.get();
        }

        String key = buildKey(semester);
        String lockKey = "lock:" + key;

        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                if (!"true".equals(value) && !"false".equals(value)) {
                    logger.warn("[Redis][GradeReleasePolicy] Invalid cached value: {}, evicting", value);
                    evict(semester);
                } else {
                    Boolean isReleased = "true".equals(value);
                    circuitBreaker.recordSuccess();
                    logger.debug("[Redis][GradeReleasePolicy] Cache HIT: semester={}, isReleased={}", semester, isReleased);
                    return isReleased;
                }
            }

            RedisLock lock = new RedisLock(redisTemplate, lockKey, LOCK_TTL);
            if (lock.tryLock()) {
                try {
                    value = redisTemplate.opsForValue().get(key);
                    if (value != null) {
                        if (!"true".equals(value) && !"false".equals(value)) {
                            logger.warn("[Redis][GradeReleasePolicy] Invalid cached value after lock: {}, evicting", value);
                            evict(semester);
                        } else {
                            Boolean isReleased = "true".equals(value);
                            circuitBreaker.recordSuccess();
                            logger.debug("[Redis][GradeReleasePolicy] Cache HIT after lock: semester={}, isReleased={}", semester, isReleased);
                            return isReleased;
                        }
                    }

                    logger.debug("[Redis][GradeReleasePolicy] Cache MISS, loading from DB: semester={}", semester);
                    Boolean isReleased = loader.get();
                    putInternal(key, isReleased);
                    return isReleased;
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
                        if (!"true".equals(value) && !"false".equals(value)) {
                            logger.warn("[Redis][GradeReleasePolicy] Invalid cached value after retry: {}, evicting", value);
                            evict(semester);
                        } else {
                            Boolean isReleased = "true".equals(value);
                            circuitBreaker.recordSuccess();
                            logger.debug("[Redis][GradeReleasePolicy] Cache HIT after retry {}: semester={}, isReleased={}", 
                                    attempt + 1, semester, isReleased);
                            return isReleased;
                        }
                    }
                }

                logger.warn("[Redis][GradeReleasePolicy] All retries exhausted, using loader as fallback: semester={}", semester);
                return loader.get();
            }
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            logger.warn("[Redis][GradeReleasePolicy] Error in getOrLoad, using loader: {}", e.getMessage());
            return loader.get();
        }
    }

    public void put(String semester, Boolean isReleased) {
        if (circuitBreaker.isOpen()) {
            logger.debug("[Redis][GradeReleasePolicy] Circuit breaker open, skipping cache write");
            return;
        }

        String key = buildKey(semester);
        putInternal(key, isReleased);
    }

    private void putInternal(String key, Boolean isReleased) {
        try {
            Duration effectiveTtl = getEffectiveTtl();
            redisTemplate.opsForValue().set(key, String.valueOf(isReleased), effectiveTtl);
            circuitBreaker.recordSuccess();
            logger.debug("[Redis][GradeReleasePolicy] Cached: semester={}, isReleased={}, ttl={}s",
                    key.substring(key.lastIndexOf(':') + 1), isReleased, effectiveTtl.getSeconds());
        } catch (Exception e) {
            circuitBreaker.recordFailure();
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
