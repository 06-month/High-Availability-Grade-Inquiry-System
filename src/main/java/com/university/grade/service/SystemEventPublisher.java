package com.university.grade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.grade.entity.SystemEvent;
import com.university.grade.repository.command.SystemEventRepository;
import com.university.grade.util.LoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SystemEventPublisher {
    private static final Logger logger = LoggerFactory.getLogger(SystemEventPublisher.class);

    private final SystemEventRepository systemEventRepository;
    private final ObjectMapper objectMapper;

    public SystemEventPublisher(SystemEventRepository systemEventRepository, ObjectMapper objectMapper) {
        this.systemEventRepository = systemEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void publishCacheInvalidationEvent(String cacheType, Long studentId, String semester, String reason) {
        try {
            CacheInvalidationPayload payload = new CacheInvalidationPayload();
            payload.setCacheType(cacheType);
            payload.setStudentId(studentId);
            payload.setSemester(semester);
            payload.setReason(reason);
            payload.setSchemaVersion(1);

            String description = objectMapper.writeValueAsString(payload);

            SystemEvent event = new SystemEvent();
            event.setEventType("CACHE_INVALIDATE");
            event.setDescription(description);
            event.setCreatedAt(LocalDateTime.now());
            event.setProcessingStatus(SystemEvent.ProcessingStatus.PENDING);
            event.setRetryCount(0);

            systemEventRepository.save(event);
            logger.debug("Published cache invalidation event: type={}, studentId={}, semester={}, reason={}",
                    cacheType, LoggingUtil.maskStudentId(studentId), semester, reason);
        } catch (Exception e) {
            logger.error("Failed to publish cache invalidation event: type={}, studentId={}, semester={}, reason={}",
                    cacheType, LoggingUtil.maskStudentId(studentId), semester, reason, e);
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