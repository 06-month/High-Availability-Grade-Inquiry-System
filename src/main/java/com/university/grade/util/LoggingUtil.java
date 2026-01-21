package com.university.grade.util;

public class LoggingUtil {
    public static String maskStudentId(Long studentId) {
        if (studentId == null) {
            return "null";
        }
        String id = String.valueOf(studentId);
        if (id.length() <= 4) {
            return "****";
        }
        return "****" + id.substring(id.length() - 4);
    }
}
