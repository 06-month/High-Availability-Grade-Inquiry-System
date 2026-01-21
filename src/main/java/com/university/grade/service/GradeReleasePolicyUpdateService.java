package com.university.grade.service;

import com.university.grade.repository.command.GradeReleasePolicyCommandRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class GradeReleasePolicyUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(GradeReleasePolicyUpdateService.class);

    private final GradeReleasePolicyCommandRepository policyRepository;
    private final CacheInvalidationService cacheInvalidationService;

    public GradeReleasePolicyUpdateService(
            GradeReleasePolicyCommandRepository policyRepository,
            CacheInvalidationService cacheInvalidationService) {
        this.policyRepository = policyRepository;
        this.cacheInvalidationService = cacheInvalidationService;
    }

    @Transactional
    public void updateReleasePolicy(String semester, boolean isReleased) {
        logger.debug("Updating grade release policy: semester={}, isReleased={}", semester, isReleased);
        policyRepository.updateReleaseStatus(semester, isReleased);
        
        TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    try {
                        cacheInvalidationService.invalidatePolicy(semester);
                    } catch (Exception e) {
                        logger.warn("Failed to invalidate policy cache after commit: semester={}", semester, e);
                    }
                }
            }
        );
    }
}
