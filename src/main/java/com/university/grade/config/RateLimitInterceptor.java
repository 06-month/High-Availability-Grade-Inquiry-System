package com.university.grade.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.grade.dto.ErrorResponse;
import com.university.grade.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final int RATE_LIMIT = 100;
    private static final int WINDOW_SECONDS = 60;

    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;
    private final ObjectMapper objectMapper;

    public RateLimitInterceptor(
            RedisTemplate<String, String> redisTemplate,
            DefaultRedisScript<Long> rateLimitScript,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return true;
        }

        String userId = authentication.getName();
        String key = "rate:limit:grade:" + userId;

        try {
            Long result = redisTemplate.execute(rateLimitScript, Collections.singletonList(key),
                    String.valueOf(RATE_LIMIT), String.valueOf(WINDOW_SECONDS));

            if (result == null || result == 0) {
                logger.debug("Rate limit exceeded for user");
                throw new RateLimitExceededException("Too many requests");
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            logger.debug("Rate limit check failed, allowing request: {}", e.getMessage());
            return true;
        }

        return true;
    }
}
