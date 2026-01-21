package com.university.grade.controller;

import com.university.grade.dto.GradeDetailResponse;
import com.university.grade.dto.GradeSummaryResponse;
import com.university.grade.service.GradeInquiryService;
import com.university.grade.util.SecurityUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@RestController
@RequestMapping("/api/v1/grades")
@Validated
public class GradeInquiryController {

    private final GradeInquiryService gradeInquiryService;

    public GradeInquiryController(GradeInquiryService gradeInquiryService) {
        this.gradeInquiryService = gradeInquiryService;
    }

    @GetMapping("/summary")
    public ResponseEntity<GradeSummaryResponse> getGradeSummary(
            @RequestParam @NotBlank @Size(max = 20) @Pattern(regexp = "^\\d{4}-[12]$", message = "Semester must be in format YYYY-1 or YYYY-2") String semester,
            Authentication authentication) {
        Long studentId = SecurityUtil.extractStudentIdFromAuthentication(authentication);
        if (studentId == null || studentId <= 0) {
            throw new AuthenticationException("Invalid authentication") {
            };
        }

        GradeSummaryResponse response = gradeInquiryService.getGradeSummary(studentId, semester);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/list")
    public ResponseEntity<List<GradeDetailResponse>> getGradeList(
            @RequestParam @NotBlank @Size(max = 20) @Pattern(regexp = "^\\d{4}-[12]$", message = "Semester must be in format YYYY-1 or YYYY-2") String semester,
            Authentication authentication) {
        Long studentId = SecurityUtil.extractStudentIdFromAuthentication(authentication);
        if (studentId == null || studentId <= 0) {
            throw new AuthenticationException("Invalid authentication") {
            };
        }

        List<GradeDetailResponse> response = gradeInquiryService.getGradeList(studentId, semester);
        return ResponseEntity.ok(response);
    }
}
