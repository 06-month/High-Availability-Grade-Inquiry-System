package com.university.grade.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface GradeDetailProjection {
    Long getEnrollmentId();
    String getCourseCode();
    String getCourseName();
    Integer getCredit();
    BigDecimal getScore();
    String getGradeLetter();
    Boolean getIsFinalized();
    LocalDateTime getFinalizedAt();
}
