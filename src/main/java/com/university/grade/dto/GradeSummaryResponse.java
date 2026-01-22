package com.university.grade.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GradeSummaryResponse {
    private Long studentId;
    private String semester;
    private BigDecimal gpa;
    private Integer totalCredits;
    private LocalDateTime updatedAt;

    public GradeSummaryResponse() {
    }

    public GradeSummaryResponse(Long studentId, String semester, BigDecimal gpa, Integer totalCredits, LocalDateTime updatedAt) {
        this.studentId = studentId;
        this.semester = semester;
        this.gpa = gpa;
        this.totalCredits = totalCredits;
        this.updatedAt = updatedAt;
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

    public BigDecimal getGpa() {
        return gpa;
    }

    public void setGpa(BigDecimal gpa) {
        this.gpa = gpa;
    }

    public Integer getTotalCredits() {
        return totalCredits;
    }

    public void setTotalCredits(Integer totalCredits) {
        this.totalCredits = totalCredits;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
