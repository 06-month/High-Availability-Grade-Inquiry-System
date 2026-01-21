package com.university.grade.cache;

import com.university.grade.dto.GradeSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class CacheStampedeTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8")
            .withDatabaseName("grade_portal")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.datasource.master.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.master.username", mysql::getUsername);
        registry.add("spring.datasource.master.password", mysql::getPassword);
        registry.add("spring.datasource.replica.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.replica.username", mysql::getUsername);
        registry.add("spring.datasource.replica.password", mysql::getPassword);
    }

    @Autowired
    private GradeSummaryCache gradeSummaryCache;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    void testConcurrentCacheMiss() throws InterruptedException {
        Long studentId = 1L;
        String semester = "2024-1";
        
        String key = "v1:grade:summary:" + studentId + ":" + semester;
        redisTemplate.delete(key);
        redisTemplate.delete("lock:" + key);

        AtomicInteger loaderCallCount = new AtomicInteger(0);
        int concurrentRequests = 50;
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            Thread t = new Thread(() -> {
                try {
                    latch.countDown();
                    latch.await();
                    
                    gradeSummaryCache.getOrLoad(studentId, semester, () -> {
                        loaderCallCount.incrementAndGet();
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return new GradeSummaryResponse(studentId, semester, 
                                BigDecimal.valueOf(3.5), 15, LocalDateTime.now());
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            threads.add(t);
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        assertThat(loaderCallCount.get()).isLessThanOrEqualTo(5);
    }
}
