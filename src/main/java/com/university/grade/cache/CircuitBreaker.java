package com.university.grade.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CircuitBreaker {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);

    private volatile boolean isOpen = false;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final int failureThreshold;
    private final long timeoutMillis;

    public CircuitBreaker(int failureThreshold, long timeoutMillis) {
        this.failureThreshold = failureThreshold;
        this.timeoutMillis = timeoutMillis;
    }

    public boolean isOpen() {
        if (isOpen) {
            long now = System.currentTimeMillis();
            if (now - lastFailureTime.get() > timeoutMillis) {
                logger.debug("Circuit breaker entering half-open state");
                isOpen = false;
                failureCount.set(0);
                return false;
            }
            return true;
        }
        return false;
    }

    public void recordSuccess() {
        if (isOpen) {
            logger.debug("Circuit breaker closed after successful operation");
            isOpen = false;
            failureCount.set(0);
        } else {
            failureCount.set(0);
        }
    }

    public void recordFailure() {
        long now = System.currentTimeMillis();
        lastFailureTime.set(now);
        int count = failureCount.incrementAndGet();
        if (count >= failureThreshold && !isOpen) {
            logger.warn("Circuit breaker opened after {} failures", count);
            isOpen = true;
        }
    }

    public void reset() {
        isOpen = false;
        failureCount.set(0);
        lastFailureTime.set(0);
    }
}
