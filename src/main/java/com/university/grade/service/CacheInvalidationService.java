package com.university.grade.service;

import com.university.grade.cache.GradeListCache;
import com.university.grade.cache.GradeReleasePolicyCache;
import com.university.grade.cache.GradeSummaryCache;
import com.university.grade.util.LoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CacheInvalidationService {
    private static final Logger logger = LoggerFactory.getLogger(CacheInvalidationService.class);

    private final GradeSummaryCache gradeSummaryCache;
    private final GradeListCache gradeListCache;
    private final GradeReleasePolicyCache policyCache;

    public CacheInvalidationService(
            GradeSummaryCache gradeSummaryCache,
            GradeListCache gradeListCache,
            GradeReleasePolicyCache policyCache) {
        this.gradeSummaryCache = gradeSummaryCache;
        this.gradeListCache = gradeListCache;
        this.policyCache = policyCache;
    }

    public void invalidateGradeSummary(Long studentId, String semester) {
        logger.debug("Invalidating grade summary cache: studentId={}, semester={}", 
                LoggingUtil.maskStudentId(studentId), semester);
        gradeSummaryCache.evict(studentId, semester);
    }

    public void invalidateGradeList(Long studentId, String semester) {
        logger.debug("Invalidating grade list cache: studentId={}, semester={}", 
                LoggingUtil.maskStudentId(studentId), semester);
        gradeListCache.evict(studentId, semester);
    }

    public void invalidatePolicy(String semester) {
        logger.debug("Invalidating policy cache: semester={}", semester);
        policyCache.evict(semester);
    }

    public void invalidateAllForStudent(Long studentId, String semester) {
        logger.debug("Invalidating all caches for student: studentId={}, semester={}", 
                LoggingUtil.maskStudentId(studentId), semester);
        invalidateGradeSummary(studentId, semester);
        invalidateGradeList(studentId, semester);
    }
}
