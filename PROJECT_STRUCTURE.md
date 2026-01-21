# 프로젝트 구조

## 📁 디렉토리 구조

```
High-Availability-Grade-Inquiry-System/
├── src/
│   ├── main/
│   │   ├── java/com/university/grade/
│   │   │   ├── Application.java
│   │   │   ├── cache/                    # Redis 캐시 구현
│   │   │   ├── config/                   # 설정 클래스들
│   │   │   │   ├── DatabaseConfig.java  # Master/Slave DB 설정
│   │   │   │   ├── RedisConfig.java      # Redis 설정
│   │   │   │   └── SecurityConfig.java   # JWT 보안 설정
│   │   │   ├── controller/               # REST API 컨트롤러
│   │   │   ├── dto/                      # 데이터 전송 객체
│   │   │   ├── entity/                   # JPA 엔티티
│   │   │   ├── repository/               # 데이터 접근 계층
│   │   │   │   ├── command/              # 쓰기 전용 리포지토리
│   │   │   │   ├── query/                # 읽기 전용 리포지토리
│   │   │   │   └── projection/           # 프로젝션 인터페이스
│   │   │   ├── service/                  # 비즈니스 로직
│   │   │   └── util/                     # 유틸리티 클래스
│   │   └── resources/
│   │       ├── application.yml           # 기본 설정
│   │       ├── application-nks.yml       # NKS 운영 설정
│   │       └── static/                   # 정적 웹 파일
│   │           ├── login/                # 로그인 페이지
│   │           └── main/                 # 메인 페이지
├── k8s/                                  # Kubernetes 매니페스트
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── was-deployment.yaml               # WAS Pod 배포 설정
│   ├── web-deployment.yaml               # Web Pod 배포 설정
│   ├── services.yaml                     # 서비스 설정
│   └── hpa.yaml                          # Auto Scaling 설정
├── web/                                  # Web Pod 관련 파일
│   ├── Dockerfile                        # Nginx 이미지 빌드
│   └── nginx.conf                        # Nginx 설정
├── Dockerfile                            # WAS Pod 이미지 빌드
├── deploy-nks.sh                         # NKS 배포 스크립트
├── build.gradle                          # Gradle 빌드 설정
├── schema.sql                            # 데이터베이스 스키마
├── NKS_DEPLOYMENT_GUIDE.md               # NKS 배포 가이드
└── README.md
```

## 🏗️ 아키텍처 구성

### 1. Web Tier (Frontend)
- **기술**: Nginx + Static Files
- **역할**: 정적 파일 서빙, 리버스 프록시
- **배포**: Web Pod (Kubernetes)

### 2. Application Tier (Backend)
- **기술**: Spring Boot 3.2.0 + Java 17
- **역할**: REST API, 비즈니스 로직
- **배포**: WAS Pod (Kubernetes)

### 3. Data Tier
- **MySQL**: Master(Write) + Slave(Read) 구성
- **Redis**: 캐시 서버
- **배포**: NCP Cloud DB 서비스

## 🔄 데이터 플로우

```
[사용자] → [Web Pod] → [WAS Pod] → [MySQL Master/Slave + Redis]
```

1. **정적 파일 요청**: Web Pod(Nginx)에서 직접 서빙
2. **API 요청**: Web Pod → WAS Pod로 프록시
3. **데이터 조회**: WAS Pod → MySQL Slave (읽기)
4. **데이터 변경**: WAS Pod → MySQL Master (쓰기)
5. **캐시**: WAS Pod ↔ Redis

## 🚀 배포 환경별 설정

### NKS 환경
- **프로파일**: `nks`
- **데이터**: 실제 MySQL + Redis
- **보안**: JWT 인증 활성화
- **포트**: 80 (Web), 8080 (WAS)

## 📊 모니터링 및 관리

### Health Check
- **Web**: `/nginx-health`
- **WAS**: `/actuator/health`

### Metrics
- **Prometheus**: `/actuator/prometheus`
- **Application Info**: `/actuator/info`

### Auto Scaling
- **WAS**: CPU 70%, Memory 80% 기준
- **Web**: CPU 70% 기준
- **최소/최대**: WAS(3-10), Web(2-5)

## 🔒 보안 설정

### 인증/인가
- **JWT**: OAuth2 Resource Server

### 네트워크
- **Internal**: ClusterIP 서비스
- **External**: LoadBalancer 서비스

### 데이터
- **암호화**: TLS/SSL 연결
- **Secret**: Kubernetes Secret 사용