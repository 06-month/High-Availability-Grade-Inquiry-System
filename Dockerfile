# Multi-stage 빌드 사용
# Stage 1: 빌드 단계
FROM gradle:7.6-jdk17 AS build
WORKDIR /app

# Gradle 캐시 최적화
COPY backend/build.gradle backend/settings.gradle ./
COPY backend/gradle ./gradle
RUN gradle dependencies --no-daemon || true

# 소스 코드 복사 및 빌드
COPY backend/src ./src
RUN gradle build -x test --no-daemon

# Stage 2: 실행 단계
FROM openjdk:17-jdk-slim
WORKDIR /app

# curl 설치 (헬스체크용)
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 헬스체크 설정
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
