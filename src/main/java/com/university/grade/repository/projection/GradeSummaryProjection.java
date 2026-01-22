package com.university.grade.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface GradeSummaryProjection {
    Long getStudentId();
    String getSemester();
    BigDecimal getGpa();
    Integer getTotalCredits();
    LocalDateTime getUpdatedAt();
}
