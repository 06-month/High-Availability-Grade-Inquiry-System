package com.university.grade.repository.query;

import com.university.grade.entity.GradeSummary;
import com.university.grade.repository.projection.GradeSummaryProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GradeSummaryQueryRepository extends Repository<GradeSummary, Long> {
    
    @Query(value = 
        "SELECT " +
        "  student_id as studentId, " +
        "  semester, " +
        "  gpa, " +
        "  total_credits as totalCredits, " +
        "  updated_at as updatedAt " +
        "FROM GRADE_SUMMARY " +
        "WHERE student_id = :studentId AND semester = :semester",
        nativeQuery = true)
    Optional<GradeSummaryProjection> findSummaryByStudentIdAndSemester(
        @Param("studentId") Long studentId, 
        @Param("semester") String semester
    );
}
