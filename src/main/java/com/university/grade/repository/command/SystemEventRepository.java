package com.university.grade.repository.command;

import com.university.grade.entity.SystemEvent;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SystemEventRepository extends Repository<SystemEvent, Long> {

    SystemEvent save(SystemEvent event);

    @Query(value = 
        "SELECT * FROM SYSTEM_EVENTS " +
        "WHERE event_type = :eventType " +
        "  AND processing_status = 'PENDING' " +
        "  AND (retry_count < 3 OR retry_count IS NULL) " +
        "ORDER BY created_at ASC " +
        "LIMIT :limit",
        nativeQuery = true)
    List<SystemEvent> findPendingEventsByType(
        @Param("eventType") String eventType,
        @Param("limit") int limit
    );

    @Modifying
    @Query(value = 
        "UPDATE SYSTEM_EVENTS " +
        "SET processing_status = 'PROCESSING' " +
        "WHERE event_id = :eventId " +
        "  AND processing_status = 'PENDING'",
        nativeQuery = true)
    int claimEventForProcessing(@Param("eventId") Long eventId);

    @Modifying
    @Query(value = 
        "UPDATE SYSTEM_EVENTS " +
        "SET processing_status = 'COMPLETED', " +
        "    processed_at = :processedAt " +
        "WHERE event_id = :eventId " +
        "  AND processing_status = 'PROCESSING'",
        nativeQuery = true)
    int markEventCompleted(
        @Param("eventId") Long eventId,
        @Param("processedAt") LocalDateTime processedAt
    );

    @Modifying
    @Query(value = 
        "UPDATE SYSTEM_EVENTS " +
        "SET processing_status = 'FAILED', " +
        "    processed_at = :processedAt, " +
        "    retry_count = retry_count + 1 " +
        "WHERE event_id = :eventId " +
        "  AND processing_status = 'PROCESSING'",
        nativeQuery = true)
    int markEventFailed(
        @Param("eventId") Long eventId,
        @Param("processedAt") LocalDateTime processedAt
    );

    @Query(value = 
        "SELECT * FROM SYSTEM_EVENTS " +
        "WHERE processing_status = 'PROCESSING' " +
        "  AND created_at < :threshold",
        nativeQuery = true)
    List<SystemEvent> findStuckProcessingEvents(@Param("threshold") LocalDateTime threshold);

    @Modifying
    @Query(value = 
        "UPDATE SYSTEM_EVENTS " +
        "SET processing_status = 'PENDING', " +
        "    processed_at = NULL " +
        "WHERE event_id = :eventId " +
        "  AND processing_status = 'PROCESSING'",
        nativeQuery = true)
    int resetStuckEvent(@Param("eventId") Long eventId);
}
