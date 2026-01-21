package com.university.grade.service;

import com.university.grade.cache.GradeReleasePolicyCache;
import com.university.grade.repository.command.GradeReleasePolicyCommandRepository;
import com.university.grade.repository.projection.GradeReleasePolicyProjection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class GradeReleasePolicyService {
    private static final Logger logger = LoggerFactory.getLogger(GradeReleasePolicyService.class);

    private final GradeReleasePolicyCache policyCache;
    private final GradeReleasePolicyCommandRepository policyCommandRepository;

    public GradeReleasePolicyService(
            GradeReleasePolicyCache policyCache,
            GradeReleasePolicyCommandRepository policyCommandRepository) {
        this.policyCache = policyCache;
        this.policyCommandRepository = policyCommandRepository;
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public boolean isGradeReleasedCached(String semester) {
        return policyCache.getOrLoad(semester, () -> {
            Optional<GradeReleasePolicyProjection> policyOpt = policyCommandRepository.findReleaseStatusBySemester(semester);
            if (policyOpt.isEmpty()) {
                logger.warn("Grade release policy not found for semester: {}", semester);
                return false;
            }

            GradeReleasePolicyProjection policy = policyOpt.get();
            Boolean isReleased = policy.getIsReleased();

            logger.debug("Retrieved and cached grade release policy from Master DB: semester={}, isReleased={}", 
                semester, isReleased);

            return isReleased;
        });
    }

    @Transactional(readOnly = false, propagation = Propagation.REQUIRES_NEW)
    public boolean isGradeReleasedStrict(String semester) {
        Optional<GradeReleasePolicyProjection> policyOpt = policyCommandRepository.findReleaseStatusBySemester(semester);
        if (policyOpt.isEmpty()) {
            logger.warn("Grade release policy not found for semester: {}", semester);
            return false;
        }

        GradeReleasePolicyProjection policy = policyOpt.get();
        Boolean isReleased = policy.getIsReleased();

        logger.debug("Retrieved grade release policy from Master DB (strict): semester={}, isReleased={}", 
            semester, isReleased);

        return isReleased;
    }
}
