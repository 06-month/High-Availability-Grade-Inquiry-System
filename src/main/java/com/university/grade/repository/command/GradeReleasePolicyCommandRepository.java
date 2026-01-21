package com.university.grade.repository.command;

import com.university.grade.entity.GradeReleasePolicy;
import com.university.grade.repository.projection.GradeReleasePolicyProjection;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GradeReleasePolicyCommandRepository extends Repository<GradeReleasePolicy, Long> {
    
    @Query(value = 
        "SELECT is_released as isReleased " +
        "FROM GRADE_RELEASE_POLICY " +
        "WHERE semester = :semester",
        nativeQuery = true)
    Optional<GradeReleasePolicyProjection> findReleaseStatusBySemester(@Param("semester") String semester);

    @Modifying
    @Query(value = 
        "UPDATE GRADE_RELEASE_POLICY " +
        "SET is_released = :isReleased, updated_at = CURRENT_TIMESTAMP " +
        "WHERE semester = :semester",
        nativeQuery = true)
    void updateReleaseStatus(@Param("semester") String semester, @Param("isReleased") boolean isReleased);
}
