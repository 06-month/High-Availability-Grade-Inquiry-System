package com.university.grade.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class SecurityUtil {
    private static final Logger logger = LoggerFactory.getLogger(SecurityUtil.class);
    private static boolean allowStudentIdFallback = false;

    @Value("${app.security.allow-studentid-fallback:false}")
    private boolean allowStudentIdFallbackProperty;

    @PostConstruct
    public void init() {
        allowStudentIdFallback = allowStudentIdFallbackProperty;
    }

    public static Long extractStudentIdFromAuthentication(Authentication authentication) {
        if (authentication == null) {
            logger.warn("Authentication is null");
            return null;
        }

        if (!authentication.isAuthenticated()) {
            logger.warn("Authentication is not authenticated");
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal == null) {
            logger.warn("Principal is null");
            return null;
        }

        if (authentication instanceof JwtAuthenticationToken) {
            Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
            Object studentIdClaim = jwt.getClaim("studentId");
            if (studentIdClaim != null) {
                try {
                    if (studentIdClaim instanceof Number) {
                        return ((Number) studentIdClaim).longValue();
                    }
                    return Long.parseLong(studentIdClaim.toString());
                } catch (NumberFormatException e) {
                    logger.warn("Failed to parse studentId from JWT claim: {}", studentIdClaim);
                    return null;
                }
            }
        }

        if (!allowStudentIdFallback) {
            logger.warn("StudentId claim not found and fallback is disabled");
            return null;
        }

        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            String username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            try {
                return Long.parseLong(username);
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse studentId from username: {}", username);
                return null;
            }
        }

        if (principal instanceof String) {
            try {
                return Long.parseLong((String) principal);
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse studentId from principal string: {}", principal);
                return null;
            }
        }

        logger.warn("Unsupported principal type: {}", principal.getClass().getName());
        return null;
    }

    public static Long getCurrentStudentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return extractStudentIdFromAuthentication(authentication);
    }
}
