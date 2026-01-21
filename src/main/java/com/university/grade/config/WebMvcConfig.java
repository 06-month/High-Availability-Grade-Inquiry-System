package com.university.grade.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // 정적 리소스 매핑 (CSS, JS, 이미지 등)
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");

        // 루트 경로에서 정적 파일 직접 접근 허용
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(false);
    }

    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        // 루트 경로를 로그인 페이지로 리다이렉트
        registry.addRedirectViewController("/", "/login/index.html");

        // 기본 인덱스 페이지들 매핑
        registry.addViewController("/login").setViewName("forward:/login/index.html");
        registry.addViewController("/main").setViewName("forward:/main/index.html");
    }
}
