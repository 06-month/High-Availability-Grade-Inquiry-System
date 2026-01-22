# Grade Portal 데이터베이스 ERD

## 개요
성적 조회 시스템의 데이터베이스 구조를 나타내는 ERD입니다.

## ERD 다이어그램

```mermaid
erDiagram
    USERS ||--o| STUDENTS : "1:1"
    USERS ||--o{ AUTH_LOGS : "1:N"
    STUDENTS ||--o{ ENROLLMENTS : "1:N"
    STUDENTS ||--o{ GRADE_SUMMARY : "1:N"
    COURSES ||--o{ ENROLLMENTS : "1:N"
    ENROLLMENTS ||--|| GRADES : "1:1"
    ENROLLMENTS ||--o{ GRADE_OBJECTIONS : "1:N"
    
    USERS {
        bigint user_id PK
        varchar login_id UK
        varchar password_hash
        varchar role
        datetime created_at
        datetime updated_at
    }
    
    STUDENTS {
        bigint student_id PK
        bigint user_id FK
        varchar student_number UK
        varchar name
        varchar department
        timestamp updated_at
    }
    
    COURSES {
        bigint course_id PK
        varchar course_code UK
        varchar course_name
        int credit
        varchar semester
        timestamp updated_at
    }
    
    ENROLLMENTS {
        bigint enrollment_id PK
        bigint student_id FK
        bigint course_id FK
        varchar semester
        timestamp updated_at
    }
    
    GRADES {
        bigint grade_id PK
        bigint enrollment_id FK UK
        decimal score
        varchar grade_letter
        tinyint is_finalized
        datetime finalized_at
        timestamp updated_at
    }
    
    GRADE_SUMMARY {
        bigint summary_id PK
        bigint student_id FK
        varchar semester
        decimal gpa
        int total_credits
        timestamp updated_at
    }
    
    GRADE_OBJECTIONS {
        bigint objection_id PK
        bigint enrollment_id FK
        varchar title
        text reason
        enum status
        text professor_reply
        datetime created_at
        datetime updated_at
    }
    
    GRADE_RELEASE_POLICY {
        bigint policy_id PK
        varchar semester UK
        tinyint is_released
        datetime release_at
        datetime updated_at
    }
    
    AUTH_LOGS {
        bigint log_id PK
        bigint user_id FK
        varchar action
        varchar ip_address
        datetime created_at
    }
    
    SYSTEM_EVENTS {
        bigint event_id PK
        varchar instance_id
        varchar event_type
        text description
        datetime created_at
        datetime processed_at
        int retry_count
        enum processing_status
    }
    
    sync_checkpoint {
        varchar table_name PK
        timestamp last_synced_at
    }
```

## 테이블 상세 정보

### 1. USERS (사용자)
- **설명**: 시스템 사용자 계정 정보
- **주요 컬럼**:
  - `user_id`: 사용자 고유 ID (PK)
  - `login_id`: 로그인 ID (UNIQUE)
  - `password_hash`: 비밀번호 해시
  - `role`: 사용자 역할 (ROLE_STUDENT, ROLE_PROFESSOR 등)
- **관계**:
  - STUDENTS와 1:1 관계
  - AUTH_LOGS와 1:N 관계

### 2. STUDENTS (학생)
- **설명**: 학생 정보
- **주요 컬럼**:
  - `student_id`: 학생 고유 ID (PK)
  - `user_id`: USERS 테이블 참조 (FK, UNIQUE)
  - `student_number`: 학번 (UNIQUE)
  - `name`: 학생 이름
  - `department`: 학과
- **관계**:
  - USERS와 1:1 관계
  - ENROLLMENTS와 1:N 관계
  - GRADE_SUMMARY와 1:N 관계

### 3. COURSES (과목)
- **설명**: 개설 과목 정보
- **주요 컬럼**:
  - `course_id`: 과목 고유 ID (PK)
  - `course_code`: 과목 코드 (UNIQUE)
  - `course_name`: 과목명
  - `credit`: 학점
  - `semester`: 학기
- **관계**:
  - ENROLLMENTS와 1:N 관계

### 4. ENROLLMENTS (수강 신청)
- **설명**: 학생의 수강 신청 내역
- **주요 컬럼**:
  - `enrollment_id`: 수강 신청 고유 ID (PK)
  - `student_id`: STUDENTS 테이블 참조 (FK)
  - `course_id`: COURSES 테이블 참조 (FK)
  - `semester`: 학기
- **제약조건**:
  - `(student_id, course_id, semester)` UNIQUE 제약
- **관계**:
  - STUDENTS와 N:1 관계
  - COURSES와 N:1 관계
  - GRADES와 1:1 관계
  - GRADE_OBJECTIONS와 1:N 관계

### 5. GRADES (성적)
- **설명**: 수강 과목별 성적 정보
- **주요 컬럼**:
  - `grade_id`: 성적 고유 ID (PK)
  - `enrollment_id`: ENROLLMENTS 테이블 참조 (FK, UNIQUE)
  - `score`: 점수 (DECIMAL)
  - `grade_letter`: 등급 (A+, A, B+ 등)
  - `is_finalized`: 확정 여부
  - `finalized_at`: 확정 일시
- **관계**:
  - ENROLLMENTS와 1:1 관계

### 6. GRADE_SUMMARY (성적 요약)
- **설명**: 학생의 학기별 성적 요약 (GPA, 총 학점)
- **주요 컬럼**:
  - `summary_id`: 요약 고유 ID (PK)
  - `student_id`: STUDENTS 테이블 참조 (FK)
  - `semester`: 학기
  - `gpa`: 학점 평균 (GPA)
  - `total_credits`: 총 학점
- **제약조건**:
  - `(student_id, semester)` UNIQUE 제약
- **관계**:
  - STUDENTS와 N:1 관계

### 7. GRADE_OBJECTIONS (이의신청)
- **설명**: 성적에 대한 이의신청 내역
- **주요 컬럼**:
  - `objection_id`: 이의신청 고유 ID (PK)
  - `enrollment_id`: ENROLLMENTS 테이블 참조 (FK)
  - `title`: 제목
  - `reason`: 사유
  - `status`: 상태 (PENDING, APPROVED, REJECTED)
  - `professor_reply`: 교수 답변
- **관계**:
  - ENROLLMENTS와 N:1 관계

### 8. GRADE_RELEASE_POLICY (성적 공개 정책)
- **설명**: 학기별 성적 공개 정책
- **주요 컬럼**:
  - `policy_id`: 정책 고유 ID (PK)
  - `semester`: 학기 (UNIQUE)
  - `is_released`: 공개 여부
  - `release_at`: 공개 일시
- **관계**: 독립 테이블

### 9. AUTH_LOGS (인증 로그)
- **설명**: 사용자 인증 로그
- **주요 컬럼**:
  - `log_id`: 로그 고유 ID (PK)
  - `user_id`: USERS 테이블 참조 (FK, NULL 허용)
  - `action`: 액션 타입
  - `ip_address`: IP 주소
  - `created_at`: 생성 일시
- **관계**:
  - USERS와 N:1 관계 (user_id가 NULL일 수 있음)

### 10. SYSTEM_EVENTS (시스템 이벤트)
- **설명**: 시스템 이벤트 로그 (캐시 무효화 등)
- **주요 컬럼**:
  - `event_id`: 이벤트 고유 ID (PK)
  - `instance_id`: 인스턴스 ID
  - `event_type`: 이벤트 타입
  - `description`: 설명
  - `created_at`: 생성 일시
  - `processed_at`: 처리 일시
  - `retry_count`: 재시도 횟수
  - `processing_status`: 처리 상태 (PENDING, PROCESSING, COMPLETED, FAILED)
- **관계**: 독립 테이블

### 11. sync_checkpoint (동기화 체크포인트)
- **설명**: 데이터 동기화 체크포인트
- **주요 컬럼**:
  - `table_name`: 테이블명 (PK)
  - `last_synced_at`: 마지막 동기화 일시
- **관계**: 독립 테이블

## 데이터 통계 (현재)

| 테이블 | 레코드 수 |
|--------|----------|
| USERS | 10,001 |
| STUDENTS | 10,000 |
| COURSES | 24 |
| ENROLLMENTS | 179,947 |
| GRADES | 179,947 |
| GRADE_SUMMARY | 30,000 |
| GRADE_OBJECTIONS | 3 |
| AUTH_LOGS | 8 |
| SYSTEM_EVENTS | 3 |

## 주요 관계 요약

1. **사용자 계정 구조**
   - USERS → STUDENTS (1:1): 한 사용자는 한 학생 정보를 가짐

2. **수강 및 성적 구조**
   - STUDENTS → ENROLLMENTS (1:N): 한 학생은 여러 수강 신청을 가짐
   - COURSES → ENROLLMENTS (1:N): 한 과목은 여러 수강 신청을 가짐
   - ENROLLMENTS → GRADES (1:1): 한 수강 신청은 한 성적을 가짐

3. **성적 요약**
   - STUDENTS → GRADE_SUMMARY (1:N): 한 학생은 여러 학기의 성적 요약을 가짐

4. **이의신청**
   - ENROLLMENTS → GRADE_OBJECTIONS (1:N): 한 수강 신청은 여러 이의신청을 가질 수 있음

5. **로그 및 이벤트**
   - USERS → AUTH_LOGS (1:N): 한 사용자는 여러 인증 로그를 가짐
   - SYSTEM_EVENTS: 독립적인 시스템 이벤트 로그

## 인덱스 정보

### 주요 인덱스
- `USERS`: `idx_role`, `idx_created_at`
- `STUDENTS`: `idx_department`, `idx_student_number`
- `COURSES`: `idx_semester`, `idx_course_code_semester`
- `ENROLLMENTS`: `idx_student_semester`, `idx_course_id`, `idx_semester`
- `GRADES`: `idx_is_finalized`, `idx_finalized_at`, `idx_grades_enrollment_finalized`
- `GRADE_SUMMARY`: `idx_updated_at`, `idx_semester`
- `GRADE_OBJECTIONS`: `idx_enrollment_id`, `idx_status_created`, `idx_status_updated`
- `AUTH_LOGS`: `idx_created_at`, `idx_action_created`, `idx_auth_user_created`, `idx_auth_ip_created`
- `SYSTEM_EVENTS`: `idx_event_type`, `idx_created_at`, `idx_retry_count`, `idx_processing_status`

## 외래키 제약조건

1. `AUTH_LOGS.user_id` → `USERS.user_id` (ON DELETE SET NULL)
2. `STUDENTS.user_id` → `USERS.user_id` (ON DELETE CASCADE)
3. `ENROLLMENTS.student_id` → `STUDENTS.student_id` (ON DELETE CASCADE)
4. `ENROLLMENTS.course_id` → `COURSES.course_id` (ON DELETE RESTRICT)
5. `GRADES.enrollment_id` → `ENROLLMENTS.enrollment_id` (ON DELETE CASCADE)
6. `GRADE_OBJECTIONS.enrollment_id` → `ENROLLMENTS.enrollment_id` (ON DELETE CASCADE)
7. `GRADE_SUMMARY.student_id` → `STUDENTS.student_id` (ON DELETE CASCADE)
