# 프로젝트 디렉토리 구조

High-Availability Grade Inquiry System의 전체 디렉토리 구조입니다.

## 전체 구조

```
High-Availability-Grade-Inquiry-System/
│
├── backend/                          # 백엔드 (Spring Boot)
│   ├── build.gradle                  # Gradle 빌드 설정
│   ├── settings.gradle               # Gradle 프로젝트 설정
│   ├── gradlew                       # Gradle wrapper (Unix)
│   ├── gradlew.bat                   # Gradle wrapper (Windows)
│   ├── gradle/                       # Gradle wrapper 파일
│   │   └── wrapper/
│   │       ├── gradle-wrapper.jar
│   │       └── gradle-wrapper.properties
│   │
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/university/grade/
│           │       ├── Application.java                    # Spring Boot 메인 클래스
│           │       │
│           │       ├── config/                             # 설정 클래스
│           │       │   ├── CacheConfig.java               # 캐시 설정 (Caffeine)
│           │       │   ├── SecurityConfig.java            # 보안 설정
│           │       │   └── WebConfig.java                  # 웹 설정 (정적 리소스)
│           │       │
│           │       ├── controller/                         # REST API 컨트롤러
│           │       │   ├── AuthController.java             # 인증 API (/api/v1/auth)
│           │       │   ├── GradeController.java            # 성적 API (/api/v1/grades)
│           │       │   └── ObjectionController.java        # 이의신청 API (/api/v1/objections)
│           │       │
│           │       ├── dto/                                # 데이터 전송 객체
│           │       │   ├── LoginRequest.java              # 로그인 요청
│           │       │   ├── LoginResponse.java             # 로그인 응답
│           │       │   ├── GradeDetailResponse.java        # 성적 상세 응답
│           │       │   ├── GradeSummaryResponse.java      # 성적 요약 응답
│           │       │   ├── ObjectionRequest.java           # 이의신청 요청
│           │       │   └── ObjectionResponse.java          # 이의신청 응답
│           │       │
│           │       ├── entity/                              # JPA 엔티티
│           │       │   ├── User.java                       # 사용자
│           │       │   ├── Student.java                    # 학생
│           │       │   ├── Course.java                     # 과목
│           │       │   ├── Enrollment.java                 # 수강 신청
│           │       │   ├── Grade.java                      # 성적
│           │       │   ├── GradeSummary.java               # 성적 요약
│           │       │   ├── GradeObjection.java               # 이의신청
│           │       │   ├── GradeReleasePolicy.java          # 성적 공개 정책
│           │       │   └── SystemEvent.java                # 시스템 이벤트
│           │       │
│           │       ├── repository/                          # JPA 리포지토리
│           │       │   ├── UserRepository.java
│           │       │   ├── StudentRepository.java
│           │       │   ├── CourseRepository.java
│           │       │   ├── EnrollmentRepository.java
│           │       │   ├── GradeRepository.java
│           │       │   ├── GradeSummaryRepository.java
│           │       │   ├── GradeObjectionRepository.java
│           │       │   ├── GradeReleasePolicyRepository.java
│           │       │   └── SystemEventRepository.java
│           │       │
│           │       ├── service/                             # 비즈니스 로직
│           │       │   ├── AuthService.java                 # 인증 서비스
│           │       │   ├── GradeInquiryService.java        # 성적 조회 서비스
│           │       │   └── ObjectionService.java           # 이의신청 서비스
│           │       │
│           │       └── exception/                           # 예외 처리
│           │           └── GlobalExceptionHandler.java     # 전역 예외 핸들러
│           │
│           └── resources/
│               └── application.yml                         # 애플리케이션 설정
│
├── frontend/                         # 프론트엔드 (정적 파일)
│   └── src/
│       ├── login/                    # 로그인 페이지
│       │   ├── index.html            # 로그인 HTML
│       │   ├── app.js                # 로그인 JavaScript
│       │   └── styles.css            # 로그인 스타일
│       │
│       └── main/                     # 메인 페이지 (성적 조회)
│           ├── index.html            # 메인 HTML
│           ├── app.js                # 메인 JavaScript
│           └── styles.css            # 메인 스타일
│
├── ERD.md                            # 데이터베이스 ERD 문서
├── sequence.md                       # 시퀀스 다이어그램
├── system.md                         # 시스템 아키텍처 다이어그램
├── README.md                         # 프로젝트 README
├── DIRECTORY_STRUCTURE.md            # 이 파일 (디렉토리 구조)
│
├── gradlew                           # 루트 Gradle wrapper (참고용)
├── gradlew.bat                       # 루트 Gradle wrapper (참고용)
└── gradle/                           # 루트 Gradle wrapper 파일
    └── wrapper/
        ├── gradle-wrapper.jar
        └── gradle-wrapper.properties
```

## 백엔드 상세 구조

### Java 패키지 구조

```
com.university.grade
├── Application                       # 메인 클래스
│
├── config/                           # 설정
│   ├── CacheConfig                   # 캐시 설정 (Caffeine, TTL: 5분)
│   ├── SecurityConfig                # Spring Security 설정
│   └── WebConfig                     # 웹 MVC 설정 (프론트엔드 서빙)
│
├── controller/                       # REST API
│   ├── AuthController                # POST /api/v1/auth/login, logout
│   ├── GradeController               # GET /api/v1/grades/semesters, summary, list
│   └── ObjectionController           # POST /api/v1/objections
│
├── dto/                              # Data Transfer Object
│   ├── LoginRequest/Response
│   ├── GradeDetailResponse
│   ├── GradeSummaryResponse
│   └── ObjectionRequest/Response
│
├── entity/                           # JPA Entity (9개)
│   ├── User                          # USERS 테이블
│   ├── Student                       # STUDENTS 테이블
│   ├── Course                        # COURSES 테이블
│   ├── Enrollment                    # ENROLLMENTS 테이블
│   ├── Grade                         # GRADES 테이블
│   ├── GradeSummary                  # GRADE_SUMMARY 테이블
│   ├── GradeObjection                # GRADE_OBJECTIONS 테이블
│   ├── GradeReleasePolicy            # GRADE_RELEASE_POLICY 테이블
│   └── SystemEvent                   # SYSTEM_EVENTS 테이블
│
├── repository/                       # JPA Repository (8개)
│   ├── UserRepository
│   ├── StudentRepository
│   ├── EnrollmentRepository
│   ├── GradeRepository
│   ├── GradeSummaryRepository
│   ├── GradeObjectionRepository
│   ├── GradeReleasePolicyRepository
│   └── SystemEventRepository
│
├── service/                          # 비즈니스 로직 (3개)
│   ├── AuthService                   # 로그인 처리
│   ├── GradeInquiryService           # 성적 조회 (캐시 적용)
│   └── ObjectionService              # 이의신청 처리 (캐시 무효화)
│
└── exception/                         # 예외 처리
    └── GlobalExceptionHandler        # 전역 예외 핸들러
```

## 프론트엔드 상세 구조

### 로그인 페이지 (`frontend/src/login/`)

- **index.html**: 로그인 폼 UI
- **app.js**: 로그인 로직
  - `/api/v1/auth/login` API 호출
  - 세션 관리 (JSESSIONID)
  - 로그인 성공 시 메인 페이지로 리다이렉트
- **styles.css**: 로그인 페이지 스타일

### 메인 페이지 (`frontend/src/main/`)

- **index.html**: 성적 조회 UI
  - 학기 선택 드롭다운
  - 성적 요약 카드
  - 성적 목록 테이블
  - 과목 상세 Drawer
  - 이의신청 Modal
- **app.js**: 성적 조회 로직
  - `fetchAvailableSemesters()`: 학기 목록 조회
  - `fetchGradeSummary()`: 성적 요약 조회
  - `fetchGradeList()`: 성적 목록 조회
  - `loadAvailableSemesters()`: 학기 목록 로드 및 드롭다운 생성
  - 이의신청 제출 로직
- **styles.css**: 메인 페이지 스타일

## 파일 통계

### 백엔드
- Java 파일: 34개
  - Entity: 9개
  - Repository: 8개
  - Controller: 3개
  - Service: 3개
  - DTO: 6개
  - Config: 3개
  - 기타: 2개

### 프론트엔드
- 정적 파일: 6개
  - HTML: 2개
  - JavaScript: 2개
  - CSS: 2개

## 주요 설정 파일

### 백엔드
- `backend/build.gradle`: Gradle 빌드 설정
- `backend/settings.gradle`: 프로젝트 설정
- `backend/src/main/resources/application.yml`: 애플리케이션 설정
  - 데이터베이스 연결 정보
  - 세션 설정 (JDBC)
  - 캐시 설정 (Caffeine)

### 프론트엔드
- 정적 파일만 포함 (빌드 도구 없음)
- 백엔드에서 직접 서빙

## 실행 방법

```bash
# 백엔드 디렉토리에서 실행
cd backend
./gradlew bootRun

# 또는 루트에서
./gradlew -p backend bootRun
```

## 접속 경로

- 로그인: http://localhost:8080/login/index.html
- 메인: http://localhost:8080/main/index.html
- API: http://localhost:8080/api/v1/...

## 참고 문서

- `ERD.md`: 데이터베이스 ERD
- `sequence.md`: 시퀀스 다이어그램
- `system.md`: 시스템 아키텍처 다이어그램
- `README.md`: 프로젝트 README
