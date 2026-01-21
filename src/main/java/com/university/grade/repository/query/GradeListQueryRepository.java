package com.university.grade.repository.query;

import com.university.grade.entity.Enrollment;
import com.university.grade.repository.projection.GradeDetailProjection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GradeListQueryRepository extends Repository<Enrollment, Long> {
    
    @Query(value = 
        "SELECT " +
        "  e.enrollment_id as enrollmentId, " +
        "  c.course_code as courseCode, " +
        "  c.course_name as courseName, " +
        "  c.credit, " +
        "  g.score, " +
        "  g.grade_letter as gradeLetter, " +
        "  g.is_finalized as isFinalized, " +
        "  g.finalized_at as finalizedAt " +
        "FROM ENROLLMENTS e " +
        "INNER JOIN COURSES c ON e.course_id = c.course_id " +
        "LEFT JOIN GRADES g ON e.enrollment_id = g.enrollment_id AND g.is_finalized = TRUE " +
        "WHERE e.student_id = :studentId AND e.semester = :semester " +
        "ORDER BY c.course_code",
        nativeQuery = true)
    List<GradeDetailProjection> findGradeDetailsByStudentIdAndSemester(
        @Param("studentId") Long studentId, 
        @Param("semester") String semester
    );
}
