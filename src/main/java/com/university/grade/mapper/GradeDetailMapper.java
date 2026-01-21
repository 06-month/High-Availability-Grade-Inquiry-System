package com.university.grade.mapper;

import com.university.grade.dto.GradeDetailResponse;
import com.university.grade.repository.projection.GradeDetailProjection;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface GradeDetailMapper {
    GradeDetailMapper INSTANCE = Mappers.getMapper(GradeDetailMapper.class);

    GradeDetailResponse toDto(GradeDetailProjection projection);
}
