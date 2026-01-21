package com.university.grade.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class DatabaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    @ConfigurationProperties("spring.datasource.master")
    public DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource masterDataSource(@Qualifier("masterDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.replica")
    public DataSourceProperties readReplicaDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource readReplicaDataSource(
            @Qualifier("readReplicaDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public DataSource routingDataSource(
            @Qualifier("masterDataSource") DataSource masterDataSource,
            @Qualifier("readReplicaDataSource") DataSource readReplicaDataSource) {

        FailoverRoutingDataSource routingDataSource = new FailoverRoutingDataSource();

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("master", masterDataSource);
        dataSourceMap.put("readReplica", readReplicaDataSource);
        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(masterDataSource);

        return routingDataSource;
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("routingDataSource") DataSource routingDataSource) {
        LazyConnectionDataSourceProxy proxy = new LazyConnectionDataSourceProxy();
        proxy.setTargetDataSource(routingDataSource);
        return proxy;
    }

    /**
     * Failover 기능이 있는 라우팅 데이터소스
     */
    public static class FailoverRoutingDataSource extends AbstractRoutingDataSource {
        private static final Logger logger = LoggerFactory.getLogger(FailoverRoutingDataSource.class);

        // 데이터소스별 실패 카운트 및 마지막 실패 시간 추적
        private final Map<String, AtomicLong> failureCount = new ConcurrentHashMap<>();
        private final Map<String, Long> lastFailureTime = new ConcurrentHashMap<>();

        // Circuit Breaker 설정
        private static final int MAX_FAILURES = 3;
        private static final long RECOVERY_TIME_MS = 30000; // 30초

        @Override
        protected Object determineCurrentLookupKey() {
            boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();

            if (isReadOnly) {
                // 읽기 전용 트랜잭션: Replica 우선, 실패 시 Master로 Failover
                if (isDataSourceAvailable("readReplica")) {
                    logger.debug("Routing to readReplica");
                    return "readReplica";
                } else {
                    logger.warn("ReadReplica is unavailable, failing over to master");
                    return "master";
                }
            } else {
                // 쓰기 트랜잭션: Master만 사용
                logger.debug("Routing to master for write operation");
                return "master";
            }
        }

        @Override
        public Connection getConnection() throws SQLException {
            String targetKey = (String) determineCurrentLookupKey();

            try {
                Connection connection = super.getConnection();
                // 연결 성공 시 실패 카운트 리셋
                resetFailureCount(targetKey);
                return connection;
            } catch (SQLException e) {
                // 연결 실패 시 실패 카운트 증가
                recordFailure(targetKey);

                // 읽기 전용이고 Replica 실패 시 Master로 재시도
                if ("readReplica".equals(targetKey)) {
                    logger.warn("ReadReplica connection failed, trying master: {}", e.getMessage());
                    try {
                        // Master로 강제 라우팅
                        return getConnectionFromMaster();
                    } catch (SQLException masterException) {
                        logger.error("Both readReplica and master failed", masterException);
                        throw masterException;
                    }
                }

                throw e;
            }
        }

        private Connection getConnectionFromMaster() throws SQLException {
            DataSource masterDataSource = (DataSource) getResolvedDataSources().get("master");
            if (masterDataSource != null) {
                Connection connection = masterDataSource.getConnection();
                resetFailureCount("master");
                return connection;
            }
            throw new SQLException("Master datasource not available");
        }

        private boolean isDataSourceAvailable(String key) {
            AtomicLong failures = failureCount.get(key);
            Long lastFailure = lastFailureTime.get(key);

            if (failures == null || failures.get() < MAX_FAILURES) {
                return true;
            }

            // Circuit Breaker: 일정 시간 후 재시도 허용
            if (lastFailure != null &&
                    System.currentTimeMillis() - lastFailure > RECOVERY_TIME_MS) {
                logger.info("Attempting to recover datasource: {}", key);
                resetFailureCount(key);
                return true;
            }

            return false;
        }

        private void recordFailure(String key) {
            failureCount.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
            lastFailureTime.put(key, System.currentTimeMillis());

            long count = failureCount.get(key).get();
            logger.warn("Datasource {} failure count: {}", key, count);

            if (count >= MAX_FAILURES) {
                logger.error("Datasource {} has been marked as unavailable after {} failures", key, count);
            }
        }

        private void resetFailureCount(String key) {
            AtomicLong failures = failureCount.get(key);
            if (failures != null && failures.get() > 0) {
                failures.set(0);
                lastFailureTime.remove(key);
                logger.info("Datasource {} has been recovered", key);
            }
        }
    }
}
