package com.university.grade.service;

import com.university.grade.cache.GradeListCache;
import com.university.grade.cache.GradeSummaryCache;
import com.university.grade.dto.GradeDetailResponse;
import com.university.grade.dto.GradeSummaryResponse;
import com.university.grade.mapper.GradeDetailMapper;
import com.university.grade.repository.projection.GradeDetailProjection;
import com.university.grade.repository.projection.GradeSummaryProjection;
import com.university.grade.repository.query.GradeListQueryRepository;
import com.university.grade.repository.query.GradeSummaryQueryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GradeInquiryService {
    private static final Logger logger = LoggerFactory.getLogger(GradeInquiryService.class);

    private final GradeSummaryCache gradeSummaryCache;
    private final GradeListCache gradeListCache;
    private final GradeReleasePolicyService gradeReleasePolicyService;
    private final GradeSummaryQueryRepository gradeSummaryRepository;
    private final GradeListQueryRepository gradeListRepository;
    private final GradeDetailMapper gradeDetailMapper;
    private final Counter cacheHitCounterSummary;
    private final Counter cacheMissCounterSummary;
    private final Counter cacheHitCounterList;
    private final Counter cacheMissCounterList;
    private final Timer dbQueryTimerSummary;
    private final Timer dbQueryTimerList;
    private final boolean policyStrictCheckEnabled;

    public GradeInquiryService(
            GradeSummaryCache gradeSummaryCache,
            GradeListCache gradeListCache,
            GradeReleasePolicyService gradeReleasePolicyService,
            GradeSummaryQueryRepository gradeSummaryRepository,
            GradeListQueryRepository gradeListRepository,
            GradeDetailMapper gradeDetailMapper,
            MeterRegistry meterRegistry,
            @Value("${app.policy.strict-check-enabled:false}") boolean policyStrictCheckEnabled) {
        this.gradeSummaryCache = gradeSummaryCache;
        this.gradeListCache = gradeListCache;
        this.gradeReleasePolicyService = gradeReleasePolicyService;
        this.gradeSummaryRepository = gradeSummaryRepository;
        this.gradeListRepository = gradeListRepository;
        this.gradeDetailMapper = gradeDetailMapper;
        this.cacheHitCounterSummary = Counter.builder("grade.cache.hit")
                .tag("type", "summary")
                .register(meterRegistry);
        this.cacheMissCounterSummary = Counter.builder("grade.cache.miss")
                .tag("type", "summary")
                .register(meterRegistry);
        this.cacheHitCounterList = Counter.builder("grade.cache.hit")
                .tag("type", "list")
                .register(meterRegistry);
        this.cacheMissCounterList = Counter.builder("grade.cache.miss")
                .tag("type", "list")
                .register(meterRegistry);
        this.dbQueryTimerSummary = Timer.builder("grade.db.query")
                .tag("type", "summary")
                .register(meterRegistry);
        this.dbQueryTimerList = Timer.builder("grade.db.query")
                .tag("type", "list")
                .register(meterRegistry);
        this.policyStrictCheckEnabled = policyStrictCheckEnabled;

        logger.info("GradeInquiryService initialized with policyStrictCheckEnabled={}", policyStrictCheckEnabled);
    }

    @Transactional(readOnly = true)
    public GradeSummaryResponse getGradeSummary(Long studentId, String semester) {
        boolean isReleased = gradeReleasePolicyService.isGradeReleasedCached(semester);
        if (!isReleased) {
            logger.warn("Grade inquiry rejected - not released: semester={}", semester);
            throw new IllegalStateException("성적 공개 기간이 아닙니다.");
        }

        // Check cache first to accurately track hit/miss
        Optional<GradeSummaryResponse> cached = gradeSummaryCache.get(studentId, semester);
        if (cached.isPresent()) {
            cacheHitCounterSummary.increment();
            logger.debug("Cache HIT - Retrieved grade summary from cache: semester={}", semester);
            return cached.get();
        }

        // Cache miss - load from DB
        cacheMissCounterSummary.increment();
        GradeSummaryResponse response = gradeSummaryCache.getOrLoad(studentId, semester, () -> {
            try {
                return dbQueryTimerSummary.recordCallable(() -> {
                    var summaryOpt = gradeSummaryRepository.findSummaryByStudentIdAndSemester(studentId, semester);
                    if (summaryOpt.isEmpty()) {
                        logger.warn("Grade summary not found: semester={}", semester);
                        throw new IllegalArgumentException("성적 요약 정보를 찾을 수 없습니다.");
                    }

                    GradeSummaryProjection summary = summaryOpt.get();
                    GradeSummaryResponse result = new GradeSummaryResponse();
                    result.setStudentId(summary.getStudentId());
                    result.setSemester(summary.getSemester());
                    result.setGpa(summary.getGpa());
                    result.setTotalCredits(summary.getTotalCredits());
                    result.setUpdatedAt(summary.getUpdatedAt());

                    logger.debug("Cache MISS - Retrieved grade summary from DB: semester={}", semester);
                    return result;
                });
            } catch (Exception e) {
                logger.error("Failed to load grade summary from DB: semester={}", semester, e);
                throw new RuntimeException("성적 요약 정보를 불러오는데 실패했습니다.", e);
            }
        });

        // Optional strict check - disabled by default for performance
        if (policyStrictCheckEnabled && response != null) {
            boolean isStrictReleased = gradeReleasePolicyService.isGradeReleasedStrict(semester);
            if (!isStrictReleased) {
                gradeSummaryCache.evict(studentId, semester);
                logger.warn("Grade inquiry rejected - policy changed to not released: semester={}", semester);
                throw new IllegalStateException("성적 공개 기간이 아닙니다.");
            }
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<GradeDetailResponse> getGradeList(Long studentId, String semester) {
        boolean isReleased = gradeReleasePolicyService.isGradeReleasedCached(semester);
        if (!isReleased) {
            logger.warn("Grade inquiry rejected - not released: semester={}", semester);
            throw new IllegalStateException("성적 공개 기간이 아닙니다.");
        }

        // Check cache first to accurately track hit/miss
        Optional<List<GradeDetailResponse>> cached = gradeListCache.get(studentId, semester);
        if (cached.isPresent()) {
            cacheHitCounterList.increment();
            logger.debug("Cache HIT - Retrieved grade list from cache: semester={}", semester);
            return cached.get();
        }

        // Cache miss - load from DB
        cacheMissCounterList.increment();
        List<GradeDetailResponse> response = gradeListCache.getOrLoad(studentId, semester, () -> {
            try {
                return dbQueryTimerList.recordCallable(() -> {
                    List<GradeDetailProjection> projections = gradeListRepository
                            .findGradeDetailsByStudentIdAndSemester(studentId, semester);

                    List<GradeDetailResponse> result = projections.stream()
                            .map(gradeDetailMapper::toDto)
                            .collect(Collectors.toList());

                    logger.debug("Cache MISS - Retrieved grade list from DB: semester={}, count={}",
                            semester, result.size());

                    return result;
                });
            } catch (Exception e) {
                logger.error("Failed to load grade list from DB: semester={}", semester, e);
                throw new RuntimeException("성적 목록을 불러오는데 실패했습니다.", e);
            }
        });

        // Optional strict check - disabled by default for performance
        if (policyStrictCheckEnabled && response != null && !response.isEmpty()) {
            boolean isStrictReleased = gradeReleasePolicyService.isGradeReleasedStrict(semester);
            if (!isStrictReleased) {
                gradeListCache.evict(studentId, semester);
                logger.warn("Grade inquiry rejected - policy changed to not released: semester={}", semester);
                throw new IllegalStateException("성적 공개 기간이 아닙니다.");
            }
        }

        return response;
    }
}