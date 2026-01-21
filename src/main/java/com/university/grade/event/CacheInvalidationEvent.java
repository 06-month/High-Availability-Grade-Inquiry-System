package com.university.grade.event;

public class CacheInvalidationEvent {
    private String cacheType;
    private Long studentId;
    private String semester;

    public CacheInvalidationEvent(String cacheType, Long studentId, String semester) {
        this.cacheType = cacheType;
        this.studentId = studentId;
        this.semester = semester;
    }

    public String getCacheType() {
        return cacheType;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getSemester() {
        return semester;
    }
}
