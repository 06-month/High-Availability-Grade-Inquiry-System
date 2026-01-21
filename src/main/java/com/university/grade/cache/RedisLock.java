package com.university.grade.cache;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

public class RedisLock {
    private final RedisTemplate<String, String> redisTemplate;
    private final String lockKey;
    private final String lockValue;
    private final Duration lockTtl;
    private final DefaultRedisScript<Long> unlockScript;

    public RedisLock(RedisTemplate<String, String> redisTemplate, String lockKey, Duration lockTtl) {
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey;
        this.lockValue = UUID.randomUUID().toString();
        this.lockTtl = lockTtl;
        this.unlockScript = createUnlockScript();
    }

    public boolean tryLock() {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, lockTtl);
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock() {
        try {
            redisTemplate.execute(unlockScript, Collections.singletonList(lockKey), lockValue);
        } catch (Exception e) {
        }
    }

    private DefaultRedisScript<Long> createUnlockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "  return redis.call('del', KEYS[1]) " +
                "else " +
                "  return 0 " +
                "end"
        );
        script.setResultType(Long.class);
        return script;
    }
}
