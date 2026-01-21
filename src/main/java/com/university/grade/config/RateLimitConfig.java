package com.university.grade.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

@Configuration
public class RateLimitConfig {

    @Bean
    public DefaultRedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                "local key = KEYS[1] " +
                "local limit = tonumber(ARGV[1]) " +
                "local window = tonumber(ARGV[2]) " +
                "local current = redis.call('INCR', key) " +
                "if current == 1 then " +
                "  redis.call('EXPIRE', key, window) " +
                "end " +
                "if current > limit then " +
                "  return 0 " +
                "end " +
                "return 1"
        );
        script.setResultType(Long.class);
        return script;
    }
}
