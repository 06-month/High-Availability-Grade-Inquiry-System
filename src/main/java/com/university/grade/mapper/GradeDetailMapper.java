package com.university.grade.mapper;

import com.university.grade.dto.GradeDetailResponse;
import com.university.grade.repository.projection.GradeDetailProjection;
import org.springframework.stereotype.Component;

@Component
public class GradeDetailMapper {

    public GradeDetailResponse toDto(GradeDetailProjection projection) {
        if (projection == null) {
            return null;
        }

        GradeDetailResponse response = new GradeDetailResponse();
        response.setEnrollmentId(projection.getEnrollmentId());
        response.setCourseCode(projection.getCourseCode());
        response.setCourseName(projection.getCourseName());
        response.setCredit(projection.getCredit());
        response.setScore(projection.getScore());
        response.setGradeLetter(projection.getGradeLetter());
        response.setIsFinalized(projection.getIsFinalized());
        response.setFinalizedAt(projection.getFinalizedAt());
        return response;
    }
}
