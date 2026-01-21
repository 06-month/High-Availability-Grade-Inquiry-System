package com.university.grade.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.grade.entity.SystemEvent;
import com.university.grade.repository.command.SystemEventRepository;
import com.university.grade.service.CacheInvalidationService;
import com.university.grade.util.LoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.event-processor.enabled", havingValue = "true", matchIfMissing = false)
public class SystemEventProcessor {
    private static final Logger logger = LoggerFactory.getLogger(SystemEventProcessor.class);
    private static final int BATCH_SIZE = 100;
    private static final long STUCK_THRESHOLD_MINUTES = 5;

    private final SystemEventRepository systemEventRepository;
    private final CacheInvalidationService cacheInvalidationService;
    private final ObjectMapper objectMapper;

    public SystemEventProcessor(
            SystemEventRepository systemEventRepository,
            CacheInvalidationService cacheInvalidationService,
            ObjectMapper objectMapper) {
        this.systemEventRepository = systemEventRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processCacheInvalidationEvents() {
        List<SystemEvent> events = systemEventRepository.findPendingEventsByType("CACHE_INVALIDATE", BATCH_SIZE);
        
        for (SystemEvent event : events) {
            try {
                int claimed = systemEventRepository.claimEventForProcessing(event.getEventId());
                if (claimed == 0) {
                    logger.debug("Failed to claim event for processing: eventId={}", event.getEventId());
                    continue;
                }

                processCacheInvalidationEvent(event);

                int completed = systemEventRepository.markEventCompleted(event.getEventId(), LocalDateTime.now());
                if (completed == 0) {
                    logger.warn("Failed to mark event as completed: eventId={}", event.getEventId());
                }
            } catch (Exception e) {
                logger.error("Failed to process cache invalidation event: eventId={}", event.getEventId(), e);
                int failed = systemEventRepository.markEventFailed(event.getEventId(), LocalDateTime.now());
                if (failed == 0) {
                    logger.warn("Failed to mark event as failed: eventId={}", event.getEventId());
                }
            }
        }

        recoverStuckEvents();
    }

    private void recoverStuckEvents() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
            List<SystemEvent> stuckEvents = systemEventRepository.findStuckProcessingEvents(threshold);
            for (SystemEvent event : stuckEvents) {
                logger.warn("Recovering stuck event: eventId={}, created at {}", event.getEventId(), event.getCreatedAt());
                int reset = systemEventRepository.resetStuckEvent(event.getEventId());
                if (reset > 0) {
                    logger.info("Reset stuck event to PENDING: eventId={}", event.getEventId());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to recover stuck events", e);
        }
    }

    private void processCacheInvalidationEvent(SystemEvent event) {
        try {
            CacheInvalidationPayload payload = objectMapper.readValue(
                event.getDescription(),
                CacheInvalidationPayload.class
            );

            logger.debug("Processing cache invalidation event: type={}, studentId={}, semester={}, reason={}",
                    payload.getCacheType(), 
                    LoggingUtil.maskStudentId(payload.getStudentId()), 
                    payload.getSemester(),
                    payload.getReason());

            switch (payload.getCacheType()) {
                case "GRADE_SUMMARY":
                    cacheInvalidationService.invalidateGradeSummary(payload.getStudentId(), payload.getSemester());
                    break;
                case "GRADE_LIST":
                    cacheInvalidationService.invalidateGradeList(payload.getStudentId(), payload.getSemester());
                    break;
                case "GRADE_RELEASE_POLICY":
                    cacheInvalidationService.invalidatePolicy(payload.getSemester());
                    break;
                case "ALL":
                    cacheInvalidationService.invalidateAllForStudent(payload.getStudentId(), payload.getSemester());
                    break;
                default:
                    logger.warn("Unknown cache invalidation type: {}", payload.getCacheType());
            }
        } catch (Exception e) {
            logger.error("Failed to parse cache invalidation payload: eventId={}", event.getEventId(), e);
            throw e;
        }
    }

    private static class CacheInvalidationPayload {
        private String cacheType;
        private Long studentId;
        private String semester;
        private String reason;
        private Integer schemaVersion;

        public String getCacheType() {
            return cacheType;
        }

        public void setCacheType(String cacheType) {
            this.cacheType = cacheType;
        }

        public Long getStudentId() {
            return studentId;
        }

        public void setStudentId(Long studentId) {
            this.studentId = studentId;
        }

        public String getSemester() {
            return semester;
        }

        public void setSemester(String semester) {
            this.semester = semester;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public Integer getSchemaVersion() {
            return schemaVersion;
        }

        public void setSchemaVersion(Integer schemaVersion) {
            this.schemaVersion = schemaVersion;
        }
    }
}
