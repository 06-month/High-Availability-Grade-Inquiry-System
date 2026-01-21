### TB
graph TB
    subgraph "Naver Cloud Platform (VPC)"
        
        %% 1. Public Zone (외부 접점)
        subgraph "Public Subnet (KR-1 Zone)"
            LB[Load Balancer]
            NAT[NAT Gateway]
        end

        %% 2. Application Zone (API)
        subgraph "Private Subnet (App Layer - KR-1 & KR-2)"
            direction LR
            NKS["NKS Cluster (Kubernetes)"] -->|Auto Scaling| Pods["Backend API Pods\n(Spring Boot)"]
        end
        
        %% 3. Data Zone (DB & Cache)
        subgraph "Private Subnet (Data Layer)"
            direction TB
            
            subgraph "Cloud DB for MySQL (HA Cluster)"
                MasterDB["Master DB (KR-1)\n(Writes: Login, Log, Objections)"]
                StandbyDB["Standby DB (KR-2)\n(Failover Target)"]
                ReadReplica["Read Replica (KR-1/KR-2)\n(Reads: Grades, Courses)"]
            end
            
            subgraph "Caching Layer"
                Redis["Cloud DB for Redis\n(Session & Grade Summary)"]
            end
        end

        %% 4. Serverless Zone
        subgraph "Serverless Function"
            CF["Cloud Functions\n(성적 공개 트리거/배치)"]
        end
    end
    
    %% [Traffic Flow]
    User((사용자/학생)) -- "HTTPS (443)" --> LB
    LB --> NKS
    
    %% [App -> DB Connection Strategy]
    %% 1. Master (Write & Critical Read)
    Pods -- "1. Login (User/Log)\n2. 이의신청 (Objection)" --> MasterDB
    
    %% 2. Replica (General Read)
    Pods -- "3. 성적/강의 조회\n(Cache Miss 시)" --> ReadReplica
    
    %% 3. Cache (Fast Read)
    Pods -- "4. 성적 요약 조회\n(Cache Hit)" --> Redis
    
    %% [Internal Replication & Network]
    MasterDB -.->|Async Replication| ReadReplica
    MasterDB -.->|Sync Replication| StandbyDB
    
    %% [Serverless Flow]
    CF -- "Update Cache/DB" --> MasterDB
    CF -.->|Invalidate Cache| Redis
    NAT -- "Outbound Traffic" --> CF
    
    %% [Styling]
    style MasterDB fill:#f9f,stroke:#333,stroke-width:2px,color:black
    style StandbyDB fill:#ccc,stroke:#333,stroke-width:2px,stroke-dasharray: 5 5,color:black
    style ReadReplica fill:#cfc,stroke:#333,stroke-width:2px,color:black
    style Redis fill:#ff9,stroke:#333,stroke-width:2px
###


### sequence Diagram
sequenceDiagram
    participant User as 학생
    participant API as Backend (Pod)
    participant Redis as Redis Cache
    participant RDB_R as MySQL (Read Replica)
    participant RDB_W as MySQL (Master DB)
    participant CF as Cloud Functions

    %% ============================================================
    %% 시나리오 1: 로그인 및 인증 (보안/정합성 중요 -> Master 권장)
    %% ============================================================
    note over User, RDB_W: 1. 로그인 프로세스 (Security)
    User->>API: 1. 로그인 요청 (ID/PW)
    
    %% 보안상 Users 정보는 Master에서 읽는 것이 원칙 (Replication Lag 방지)
    API->>RDB_W: 2. 사용자 정보 조회 (users)
    RDB_W-->>API: Password Hash 반환
    
    API->>API: 비밀번호 일치 검증
    
    alt 검증 실패
        API->>RDB_W: 3. 실패 로그 기록 (auth_logs)
        API-->>User: ⛔ "로그인 실패"
    else 검증 성공
        API->>RDB_W: 3. 접속 로그 기록 (auth_logs)
        API-->>User: ✅ 토큰 발급 (로그인 성공)
    end

    %% ============================================================
    %% 시나리오 2: 성적 조회 (고부하 트래픽 -> Redis & Replica)
    %% ============================================================
    note over User, RDB_W: 2. 성적 조회 프로세스 (High Traffic)
    User->>API: 4. "내 성적 보여줘" (토큰)
    API->>Redis: 5. 캐시 확인 (grade_summary)

    alt 캐시 히트 (Cache Hit)
        Redis-->>API: 요약 데이터 즉시 반환 (0.1ms)
    else 캐시 미스 (Cache Miss)
        %% 디테일 복구: 공개 정책 확인
        API->>RDB_R: 6. 공개 여부 확인 (grade_release_policy)
        
        opt 미공개 상태 (is_released = false)
            API-->>User: ⏳ "성적 공개 기간이 아닙니다."
        end
        
        %% 공개 상태일 때만 무거운 쿼리 실행
        API->>RDB_R: 7. 전체 데이터 조인 (Join Query)
        RDB_R-->>API: 성적/과목 데이터 반환
        API->>Redis: 8. 캐시 적재 (TTL 설정)
    end
    API-->>User: 📊 성적표 화면 출력

    %% ============================================================
    %% 시나리오 3: 성적 이의신청 (쓰기 트래픽 -> Master)
    %% ============================================================
    note over User, RDB_W: 3. 성적 이의신청 (Write Traffic)
    User->>API: 9. [이의신청] 버튼 클릭 & 내용 전송
    
    alt Master DB 정상
        API->>RDB_W: 10. 이의신청 저장 (grade_objections)
        RDB_W-->>API: Commit 성공
        API-->>User: "접수되었습니다."
        
        %% 선택사항: 교수 알림 트리거
        API-)CF: (Async) 이의신청 알림 트리거
    else 💥 Master DB 장애 (HA 시연 포인트)
        API->>RDB_W: Insert 시도 -> Timeout
        API-->>User: ⚠️ "시스템 점검 중입니다. (조회는 가능)"
    end
###

### ERD
erDiagram
    %% ---------------------------------------------------------
    %% 1. 사용자 및 인증 (Master DB Write 빈번)
    %% ---------------------------------------------------------
    USERS {
        BIGINT user_id PK "Auto Increment"
        VARCHAR login_id UK "Unique Login ID"
        VARCHAR password_hash "Encrypted Password"
        VARCHAR role "ROLE_STUDENT"
        DATETIME created_at
    }

    AUTH_LOGS {
        BIGINT log_id PK
        BIGINT user_id FK
        VARCHAR action "LOGIN / FAIL"
        VARCHAR ip_address
        DATETIME created_at
    }

    STUDENTS {
        BIGINT student_id PK
        BIGINT user_id FK "1:1 Relation with USERS"
        VARCHAR student_number UK "학번"
        VARCHAR name
        VARCHAR department
    }

    %% ---------------------------------------------------------
    %% 2. 학사 및 성적 데이터 (Read Replica 조회 빈번)
    %% ---------------------------------------------------------
    COURSES {
        BIGINT course_id PK
        VARCHAR course_code UK
        VARCHAR course_name
        INT credit "학점"
        VARCHAR semester "2025-1"
    }

    ENROLLMENTS {
        BIGINT enrollment_id PK
        BIGINT student_id FK
        BIGINT course_id FK
        VARCHAR semester
    }

    GRADES {
        BIGINT grade_id PK
        BIGINT enrollment_id FK "1:1 with Enrollment"
        DECIMAL score
        VARCHAR grade_letter "A+, B0..."
        BOOLEAN is_finalized
    }

    %% ---------------------------------------------------------
    %% 3. 쓰기 트래픽 & 제어 (Master DB + Redis)
    %% ---------------------------------------------------------
    GRADE_OBJECTIONS {
        BIGINT objection_id PK
        BIGINT enrollment_id FK
        VARCHAR title
        TEXT reason
        ENUM status "PENDING, APPROVED, REJECTED"
        TEXT professor_reply
        DATETIME created_at
    }

    GRADE_RELEASE_POLICY {
        BIGINT policy_id PK
        VARCHAR semester
        BOOLEAN is_released "성적 공개 여부"
        DATETIME release_at
    }

    %% ---------------------------------------------------------
    %% 4. 성능 최적화 & 시스템 (Redis / Logs)
    %% ---------------------------------------------------------
    GRADE_SUMMARY {
        BIGINT summary_id PK
        BIGINT student_id FK
        VARCHAR semester
        DECIMAL gpa "평점 평균"
        INT total_credits
        DATETIME updated_at
    }

    SYSTEM_EVENTS {
        BIGINT event_id PK
        VARCHAR instance_id "Pod Name / IP"
        VARCHAR event_type "FAILOVER, RESTART"
        TEXT description
        DATETIME created_at
    }

    %% ---------------------------------------------------------
    %% 관계 설정 (Relationships)
    %% ---------------------------------------------------------
    
    %% User & Auth
    USERS ||--o{ AUTH_LOGS : "logs login history"
    USERS ||--|| STUDENTS : "identifies"

    %% Academic
    STUDENTS ||--o{ ENROLLMENTS : "takes"
    COURSES ||--o{ ENROLLMENTS : "is taken by"
    
    %% Grade Details
    ENROLLMENTS ||--|| GRADES : "receives"
    
    %% New Feature: Objections (성적은 수강 내역에 대해 이의신청함)
    ENROLLMENTS ||--o{ GRADE_OBJECTIONS : "files objection for"

    %% Optimization
    STUDENTS ||--o{ GRADE_SUMMARY : "has cacheable summary"

    %% Independent Tables (No FKs for Performance/Logic)
    %% GRADE_RELEASE_POLICY (Global Config)
    %% SYSTEM_EVENTS (Standalone Logs)
###