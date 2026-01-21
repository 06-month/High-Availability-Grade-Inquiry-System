package com.university.grade.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "GRADE_RELEASE_POLICY")
public class GradeReleasePolicy {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Long policyId;

    @Column(name = "semester", nullable = false, unique = true, length = 20)
    private String semester;

    @Column(name = "is_released", nullable = false)
    private Boolean isReleased;

    @Column(name = "release_at")
    private LocalDateTime releaseAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getPolicyId() {
        return policyId;
    }

    public void setPolicyId(Long policyId) {
        this.policyId = policyId;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public Boolean getIsReleased() {
        return isReleased;
    }

    public void setIsReleased(Boolean isReleased) {
        this.isReleased = isReleased;
    }

    public LocalDateTime getReleaseAt() {
        return releaseAt;
    }

    public void setReleaseAt(LocalDateTime releaseAt) {
        this.releaseAt = releaseAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
