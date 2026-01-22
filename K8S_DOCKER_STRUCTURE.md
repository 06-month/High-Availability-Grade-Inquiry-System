# 쿠버네티스 및 도커파일 구조 가이드

## 📋 목차
1. [전체 아키텍처 개요](#전체-아키텍처-개요)
2. [도커파일 구조](#도커파일-구조)
3. [쿠버네티스 매니페스트 구조](#쿠버네티스-매니페스트-구조)
4. [배포 플로우](#배포-플로우)
5. [리소스 구성 상세](#리소스-구성-상세)

---

## 🏗️ 전체 아키텍처 개요

### 시스템 아키텍처 다이어그램
```
[Internet]
    ↓
[NCP LoadBalancer]
    ↓
[Web Pod (Nginx)] ──→ [WAS Pod (Spring Boot)]
                            ↓
                    [MySQL Master/Slave + Redis]
```

### 구성 요소
- **Web Pod**: Nginx 기반 정적 파일 서빙 및 리버스 프록시
- **WAS Pod**: Spring Boot 3.2.0 + Java 17 애플리케이션 서버
- **MySQL**: Master(Write) + Slave(Read) 구성 (NCP Cloud DB)
- **Redis**: 캐시 서버 (NCP Cloud DB)
- **NCP LoadBalancer**: 외부 트래픽 분산

---

## 🐳 도커파일 구조

### 1. WAS Pod Dockerfile (루트 디렉토리)

**위치**: `/Dockerfile`

**구조 및 내용**:
```dockerfile
# Multi-stage 빌드 사용
# Stage 1: 빌드 단계
FROM gradle:7.6-jdk17 AS build
WORKDIR /app

# Gradle 캐시 최적화
COPY build.gradle settings.gradle ./
RUN gradle dependencies --no-daemon

# 소스 코드 복사 및 빌드
COPY src ./src
RUN gradle build -x test --no-daemon

# Stage 2: 실행 단계
FROM openjdk:17-jdk-slim
WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 헬스체크 설정
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**주요 특징**:
- Multi-stage 빌드로 최종 이미지 크기 최소화
- Gradle 의존성 캐시 최적화
- Java 17 기반
- Spring Boot Actuator 헬스체크 포함
- 포트 8080 노출

**빌드 명령어**:
```bash
docker build -t grade-inquiry-was:latest .
docker tag grade-inquiry-was:latest your-registry.ncloud.com/grade-inquiry-was:v1.0.0
docker push your-registry.ncloud.com/grade-inquiry-was:v1.0.0
```

---

### 2. Web Pod Dockerfile (web 디렉토리)

**위치**: `/web/Dockerfile`

**구조 및 내용**:
```dockerfile
# Nginx 기반 이미지
FROM nginx:1.25-alpine

# 기존 Nginx 설정 백업
RUN mv /etc/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf.bak

# 커스텀 Nginx 설정 파일 복사
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 정적 파일 복사 (Spring Boot static resources)
COPY ../src/main/resources/static /usr/share/nginx/html

# 포트 노출
EXPOSE 80

# 헬스체크 설정
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost/nginx-health || exit 1

# Nginx 실행
CMD ["nginx", "-g", "daemon off;"]
```

**주요 특징**:
- Alpine Linux 기반 경량 이미지
- 커스텀 Nginx 설정 적용
- Spring Boot 정적 리소스 포함
- 포트 80 노출
- 헬스체크 엔드포인트: `/nginx-health`

**빌드 명령어**:
```bash
cd web
docker build -t grade-inquiry-web:latest .
docker tag grade-inquiry-web:latest your-registry.ncloud.com/grade-inquiry-web:v1.0.0
docker push your-registry.ncloud.com/grade-inquiry-web:v1.0.0
```

---

### 3. Nginx 설정 파일 (web 디렉토리)

**위치**: `/web/nginx.conf`

**구조 및 내용**:
```nginx
upstream was_backend {
    server grade-was-service:8080;
    keepalive 32;
}

server {
    listen 80;
    server_name _;

    # 정적 파일 서빙
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
        expires 1d;
        add_header Cache-Control "public, immutable";
    }

    # API 요청 프록시
    location /api/ {
        proxy_pass http://was_backend;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # 타임아웃 설정
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Actuator 엔드포인트 프록시
    location /actuator/ {
        proxy_pass http://was_backend;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
    }

    # 헬스체크 엔드포인트
    location /nginx-health {
        access_log off;
        return 200 "healthy\n";
        add_header Content-Type text/plain;
    }
}
```

**주요 특징**:
- WAS 서비스로 API 요청 프록시
- 정적 파일 캐싱 설정
- Keepalive 연결 최적화
- 헬스체크 엔드포인트 제공

---

## ☸️ 쿠버네티스 매니페스트 구조

### 디렉토리 구조
```
k8s/
├── namespace.yaml          # 네임스페이스 정의
├── configmap.yaml          # 설정 데이터 (환경변수 등)
├── secret.yaml             # 민감 정보 (DB 비밀번호 등)
├── was-deployment.yaml     # WAS Pod 배포 설정
├── web-deployment.yaml     # Web Pod 배포 설정
├── services.yaml           # 서비스 정의 (ClusterIP, LoadBalancer)
└── hpa.yaml                # Horizontal Pod Autoscaler 설정
```

---

### 1. Namespace (namespace.yaml)

**목적**: 리소스 격리 및 관리

**구조**:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: grade-inquiry
  labels:
    app: grade-inquiry
    environment: production
```

**주요 특징**:
- 네임스페이스: `grade-inquiry`
- 모든 리소스가 이 네임스페이스에 배포됨

---

### 2. ConfigMap (configmap.yaml)

**목적**: 환경변수 및 설정 데이터 관리

**구조**:
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: grade-inquiry-config
  namespace: grade-inquiry
data:
  # Spring Boot 프로파일
  SPRING_PROFILES_ACTIVE: "nks"
  
  # JVM 옵션
  JAVA_OPTS: "-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
  
  # 애플리케이션 설정
  SERVER_PORT: "8080"
  LOGGING_LEVEL_ROOT: "INFO"
  LOGGING_LEVEL_COM_UNIVERSITY_GRADE: "DEBUG"
  
  # 데이터베이스 URL (호스트는 Secret에서)
  MASTER_DB_URL: "jdbc:mysql://${MYSQL_MASTER_HOST}:3306/university_grade?useSSL=true&serverTimezone=Asia/Seoul"
  SLAVE_DB_URL: "jdbc:mysql://${MYSQL_SLAVE_HOST}:3306/university_grade?useSSL=true&serverTimezone=Asia/Seoul"
  
  # Redis 설정
  REDIS_PORT: "6379"
  
  # Actuator 설정
  MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE: "health,info,prometheus,metrics"
```

**주요 특징**:
- 민감하지 않은 설정 데이터 저장
- 환경변수로 Pod에 주입
- 템플릿 변수 사용 가능

---

### 3. Secret (secret.yaml)

**목적**: 민감 정보 보안 관리

**구조**:
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: grade-inquiry-secret
  namespace: grade-inquiry
type: Opaque
data:
  # Base64 인코딩된 값들
  # 생성 방법: echo -n "실제값" | base64
  mysql-master-host: <base64-encoded-value>
  mysql-slave-host: <base64-encoded-value>
  db-username: <base64-encoded-value>
  db-password: <base64-encoded-value>
  redis-host: <base64-encoded-value>
  redis-password: <base64-encoded-value>
  jwt-issuer-uri: <base64-encoded-value>
```

**주요 특징**:
- Base64 인코딩 필수 (암호화 아님)
- Opaque 타입 사용
- 환경변수로 Pod에 주입
- 실제 배포 시 값 업데이트 필요

**Secret 생성 예시**:
```bash
# Base64 인코딩
echo -n "your-mysql-username" | base64
echo -n "your-mysql-password" | base64

# 또는 kubectl로 직접 생성
kubectl create secret generic grade-inquiry-secret \
  --namespace=grade-inquiry \
  --from-literal=mysql-master-host=your-mysql-master-host.ncloud.com \
  --from-literal=db-username=grade_user \
  --from-literal=db-password=your-password
```

---

### 4. WAS Deployment (was-deployment.yaml)

**목적**: Spring Boot 애플리케이션 Pod 배포

**구조**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grade-was
  namespace: grade-inquiry
  labels:
    app: grade-was
    tier: backend
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: grade-was
  template:
    metadata:
      labels:
        app: grade-was
        tier: backend
    spec:
      containers:
      - name: grade-was
        image: your-registry.ncloud.com/grade-inquiry-was:v1.0.0
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP
        
        # 환경변수
        env:
        - name: SPRING_PROFILES_ACTIVE
          valueFrom:
            configMapKeyRef:
              name: grade-inquiry-config
              key: SPRING_PROFILES_ACTIVE
        - name: JAVA_OPTS
          valueFrom:
            configMapKeyRef:
              name: grade-inquiry-config
              key: JAVA_OPTS
        - name: MYSQL_MASTER_HOST
          valueFrom:
            secretKeyRef:
              name: grade-inquiry-secret
              key: mysql-master-host
        - name: MYSQL_SLAVE_HOST
          valueFrom:
            secretKeyRef:
              name: grade-inquiry-secret
              key: mysql-slave-host
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: grade-inquiry-secret
              key: db-username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: grade-inquiry-secret
              key: db-password
        - name: REDIS_HOST
          valueFrom:
            secretKeyRef:
              name: grade-inquiry-secret
              key: redis-host
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: grade-inquiry-secret
              key: redis-password
        
        # 리소스 제한
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        
        # 헬스체크
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 5
          failureThreshold: 3
        
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        
        # 시작 프로브
        startupProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 30
```

**주요 특징**:
- 초기 레플리카: 3개
- Rolling Update 전략
- ConfigMap과 Secret에서 환경변수 주입
- 리소스 요청/제한 설정
- Liveness, Readiness, Startup 프로브 설정
- 포트 8080 노출

---

### 5. Web Deployment (web-deployment.yaml)

**목적**: Nginx 기반 Web Pod 배포

**구조**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grade-web
  namespace: grade-inquiry
  labels:
    app: grade-web
    tier: frontend
spec:
  replicas: 2
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: grade-web
  template:
    metadata:
      labels:
        app: grade-web
        tier: frontend
    spec:
      containers:
      - name: grade-web
        image: your-registry.ncloud.com/grade-inquiry-web:v1.0.0
        imagePullPolicy: Always
        ports:
        - containerPort: 80
          name: http
          protocol: TCP
        
        # 리소스 제한
        resources:
          requests:
            memory: "128Mi"
            cpu: "100m"
          limits:
            memory: "256Mi"
            cpu: "200m"
        
        # 헬스체크
        livenessProbe:
          httpGet:
            path: /nginx-health
            port: 80
          initialDelaySeconds: 10
          periodSeconds: 30
          timeoutSeconds: 3
          failureThreshold: 3
        
        readinessProbe:
          httpGet:
            path: /nginx-health
            port: 80
          initialDelaySeconds: 5
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3
```

**주요 특징**:
- 초기 레플리카: 2개
- 경량 리소스 설정
- Nginx 헬스체크 엔드포인트 사용
- 포트 80 노출

---

### 6. Services (services.yaml)

**목적**: Pod 간 통신 및 외부 노출

**구조**:
```yaml
# WAS 서비스 (ClusterIP - 내부 통신용)
apiVersion: v1
kind: Service
metadata:
  name: grade-was-service
  namespace: grade-inquiry
  labels:
    app: grade-was
spec:
  type: ClusterIP
  ports:
  - port: 8080
    targetPort: 8080
    protocol: TCP
    name: http
  selector:
    app: grade-was
---
# Web 서비스 (ClusterIP - 내부 통신용)
apiVersion: v1
kind: Service
metadata:
  name: grade-web-service
  namespace: grade-inquiry
  labels:
    app: grade-web
spec:
  type: ClusterIP
  ports:
  - port: 80
    targetPort: 80
    protocol: TCP
    name: http
  selector:
    app: grade-web
---
# LoadBalancer 서비스 (외부 노출용)
apiVersion: v1
kind: Service
metadata:
  name: grade-inquiry-lb
  namespace: grade-inquiry
  labels:
    app: grade-inquiry
spec:
  type: LoadBalancer
  ports:
  - port: 80
    targetPort: 80
    protocol: TCP
    name: http
  selector:
    app: grade-web
```

**주요 특징**:
- **ClusterIP**: Pod 간 내부 통신
  - `grade-was-service`: WAS Pod 접근용
  - `grade-web-service`: Web Pod 접근용
- **LoadBalancer**: 외부 인터넷 노출
  - `grade-inquiry-lb`: 외부 트래픽 진입점
  - NCP LoadBalancer 자동 생성

**서비스 DNS**:
- 클러스터 내부: `grade-was-service.grade-inquiry.svc.cluster.local:8080`
- 클러스터 내부 (단축): `grade-was-service:8080`

---

### 7. HPA (hpa.yaml)

**목적**: 자동 수평 스케일링

**구조**:
```yaml
# WAS HPA
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: grade-was-hpa
  namespace: grade-inquiry
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: grade-was
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30
      - type: Pods
        value: 2
        periodSeconds: 30
      selectPolicy: Max
---
# Web HPA
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: grade-web-hpa
  namespace: grade-inquiry
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: grade-web
  minReplicas: 2
  maxReplicas: 5
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
      - type: Percent
        value: 100
        periodSeconds: 30
      selectPolicy: Max
```

**주요 특징**:
- **WAS HPA**:
  - 최소 3개, 최대 10개 Pod
  - CPU 70%, Memory 80% 기준
  - 스케일 업: 최대 100% 증가 또는 2개 Pod 추가
  - 스케일 다운: 최대 50% 감소, 5분 안정화
- **Web HPA**:
  - 최소 2개, 최대 5개 Pod
  - CPU 70% 기준
  - 스케일 업: 최대 100% 증가
  - 스케일 다운: 최대 50% 감소, 5분 안정화

---

## 🚀 배포 플로우

### 1. 이미지 빌드 및 푸시
```bash
# 1. Container Registry 로그인
docker login your-registry.ncloud.com

# 2. WAS 이미지 빌드 및 푸시
docker build -t grade-inquiry-was:latest .
docker tag grade-inquiry-was:latest your-registry.ncloud.com/grade-inquiry-was:v1.0.0
docker push your-registry.ncloud.com/grade-inquiry-was:v1.0.0

# 3. Web 이미지 빌드 및 푸시
cd web
docker build -t grade-inquiry-web:latest .
docker tag grade-inquiry-web:latest your-registry.ncloud.com/grade-inquiry-web:v1.0.0
docker push your-registry.ncloud.com/grade-inquiry-web:v1.0.0
```

### 2. Kubernetes 리소스 배포 순서
```bash
# 1. 네임스페이스 생성
kubectl apply -f k8s/namespace.yaml

# 2. Secret 생성 (민감 정보)
kubectl apply -f k8s/secret.yaml

# 3. ConfigMap 생성 (설정 데이터)
kubectl apply -f k8s/configmap.yaml

# 4. Deployment 생성
kubectl apply -f k8s/was-deployment.yaml
kubectl apply -f k8s/web-deployment.yaml

# 5. Service 생성
kubectl apply -f k8s/services.yaml

# 6. HPA 생성
kubectl apply -f k8s/hpa.yaml
```

### 3. 배포 스크립트 (deploy-nks.sh)
```bash
#!/bin/bash
set -e

REGISTRY="${REGISTRY:-your-registry.ncloud.com}"
NAMESPACE="grade-inquiry"

echo "🚀 Starting deployment..."

# 네임스페이스 생성
kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -

# 리소스 배포
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/was-deployment.yaml
kubectl apply -f k8s/web-deployment.yaml
kubectl apply -f k8s/services.yaml
kubectl apply -f k8s/hpa.yaml

# 배포 상태 확인
echo "⏳ Waiting for deployments..."
kubectl wait --for=condition=available --timeout=300s deployment/grade-was -n ${NAMESPACE}
kubectl wait --for=condition=available --timeout=300s deployment/grade-web -n ${NAMESPACE}

# LoadBalancer IP 확인
echo "📊 LoadBalancer IP:"
kubectl get service grade-inquiry-lb -n ${NAMESPACE}

echo "✅ Deployment completed!"
```

---

## 📊 리소스 구성 상세

### 네임스페이스별 리소스 요약

| 리소스 타입 | 이름 | 목적 |
|------------|------|------|
| Namespace | grade-inquiry | 리소스 격리 |
| ConfigMap | grade-inquiry-config | 환경변수 설정 |
| Secret | grade-inquiry-secret | 민감 정보 |
| Deployment | grade-was | WAS Pod 관리 (3개) |
| Deployment | grade-web | Web Pod 관리 (2개) |
| Service | grade-was-service | WAS 내부 통신 (ClusterIP) |
| Service | grade-web-service | Web 내부 통신 (ClusterIP) |
| Service | grade-inquiry-lb | 외부 노출 (LoadBalancer) |
| HPA | grade-was-hpa | WAS 자동 스케일링 (3-10) |
| HPA | grade-web-hpa | Web 자동 스케일링 (2-5) |

### 포트 구성

| 서비스 | 포트 | 프로토콜 | 용도 |
|--------|------|----------|------|
| grade-was-service | 8080 | TCP | WAS 내부 통신 |
| grade-web-service | 80 | TCP | Web 내부 통신 |
| grade-inquiry-lb | 80 | TCP | 외부 인터넷 접근 |

### 리소스 제한

| Pod | CPU Request | CPU Limit | Memory Request | Memory Limit |
|-----|-------------|-----------|----------------|--------------|
| grade-was | 500m | 1000m | 1Gi | 2Gi |
| grade-web | 100m | 200m | 128Mi | 256Mi |

### 헬스체크 엔드포인트

| Pod | Liveness | Readiness | Startup |
|-----|----------|-----------|---------|
| grade-was | `/actuator/health/liveness` | `/actuator/health/readiness` | `/actuator/health` |
| grade-web | `/nginx-health` | `/nginx-health` | - |

---

## 🔍 주요 명령어

### 배포 확인
```bash
# Pod 상태 확인
kubectl get pods -n grade-inquiry

# 서비스 상태 확인
kubectl get services -n grade-inquiry

# HPA 상태 확인
kubectl get hpa -n grade-inquiry

# Deployment 상태 확인
kubectl get deployments -n grade-inquiry

# 전체 리소스 확인
kubectl get all -n grade-inquiry
```

### 로그 확인
```bash
# WAS Pod 로그
kubectl logs -f deployment/grade-was -n grade-inquiry

# Web Pod 로그
kubectl logs -f deployment/grade-web -n grade-inquiry

# 특정 Pod 로그
kubectl logs <pod-name> -n grade-inquiry
```

### 스케일링
```bash
# 수동 스케일링
kubectl scale deployment grade-was --replicas=5 -n grade-inquiry

# HPA 상태 확인
kubectl describe hpa grade-was-hpa -n grade-inquiry
```

### 업데이트
```bash
# 이미지 업데이트
kubectl set image deployment/grade-was grade-was=your-registry.ncloud.com/grade-inquiry-was:v2.0.0 -n grade-inquiry

# 롤아웃 상태 확인
kubectl rollout status deployment/grade-was -n grade-inquiry

# 롤백
kubectl rollout undo deployment/grade-was -n grade-inquiry
```

---

## 📝 참고사항

### 환경변수 매핑

**ConfigMap에서 주입**:
- `SPRING_PROFILES_ACTIVE`
- `JAVA_OPTS`
- `SERVER_PORT`
- `LOGGING_LEVEL_*`

**Secret에서 주입**:
- `MYSQL_MASTER_HOST`
- `MYSQL_SLAVE_HOST`
- `DB_USERNAME`
- `DB_PASSWORD`
- `REDIS_HOST`
- `REDIS_PASSWORD`
- `JWT_ISSUER_URI`

### 이미지 태그 전략
- 버전 태그: `v1.0.0`, `v1.0.1`, `v2.0.0`
- 최신 태그: `latest` (운영 환경 비권장)
- 배포 시 `imagePullPolicy: Always` 사용 권장

### 보안 고려사항
- Secret은 Base64 인코딩만 사용 (암호화 아님)
- 운영 환경에서는 Secret 암호화 플러그인 사용 권장
- 이미지 스캔 및 취약점 검사 수행
- Network Policy 적용 고려

---

## 🎯 요약

이 문서는 High-Availability-Grade-Inquiry-System의 쿠버네티스 및 도커파일 구조를 상세히 설명합니다:

1. **도커파일**: WAS용과 Web용 2개의 Dockerfile
2. **쿠버네티스 매니페스트**: 7개의 YAML 파일로 구성
3. **배포 전략**: Rolling Update, HPA 자동 스케일링
4. **보안**: ConfigMap과 Secret 분리 관리
5. **모니터링**: 헬스체크 및 메트릭스 엔드포인트

모든 리소스는 `grade-inquiry` 네임스페이스에 배포되며, NCP LoadBalancer를 통해 외부에 노출됩니다.
