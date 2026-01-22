sequenceDiagram
    autonumber
    actor User as "학생 (User)"
    participant App as "App Server (Pod)"
    participant Cache as "Local Cache (Memory)"
    participant Master as "MySQL Master (Write)"
    participant Slave as "MySQL Slave (Read)"

    %% ==========================================
    %% 1. 인증 단계 (Authentication)
    %% ==========================================
    rect rgb(240, 248, 255)
        note right of User: Phase 1: 로그인 (Write Traffic)
        User->>App: 1. 로그인 요청 (ID/PW)
        
        App->>Master: 2. 계정 검증 & 세션 생성 (INSERT SESSIONS)
        Master-->>App: 처리 완료
        
        App-->>User: 3. 로그인 성공 (JSESSIONID 발급)
    end

    %% ==========================================
    %% 2. 조회 단계 (Read Service)
    %% ==========================================
    rect rgb(255, 250, 240)
        note right of User: Phase 2: 성적 조회 (High Read Traffic)
        User->>App: 4. "내 성적 보여줘" (Cookie 포함)
        
        %% 세션 검증 (DB Session 방식이므로 Master 조회)
        App->>Master: 5. 세션 유효성 검증 (SELECT SESSIONS)
        Master-->>App: 유효함
        
        %% 공개 정책 확인
        App->>Slave: 6. 성적 공개 기간인지 확인 (SELECT POLICY)
        Slave-->>App: 공개 기간임 (True)
        
        %% 캐싱 로직 (Look-aside)
        App->>Cache: 7. 로컬 캐시 조회 (Key: grade:2025-1:user_A)
        
        alt Cache Hit (메모리에 있음)
            Cache-->>App: 성적 데이터 반환
            App-->>User: 8. [Fast] 즉시 응답 (0ms)
        else Cache Miss (메모리에 없음)
            App->>Slave: 9. 성적 데이터 조회 (SELECT JOINS...)
            Slave-->>App: 결과 반환
            
            App->>Cache: 10. 캐시에 적재 (TTL: 5min)
            App-->>User: 11. 응답 반환
        end
    end

    %% ==========================================
    %% 3. 쓰기 단계 (Write Service)
    %% ==========================================
    rect rgb(255, 240, 245)
        note right of User: Phase 3: 이의신청 (Transactional Write)
        User->>App: 12. 이의신청 작성 (POST /objections)
        
        Note over App, Master: 트랜잭션 시작
        
        App->>Master: 13. 기존 데이터 정합성 체크 (SELECT FOR UPDATE)
        App->>Master: 14. 이의신청 저장 (INSERT OBJECTIONS)
        App->>Master: 15. 시스템 이벤트 발행 (INSERT EVENTS)
        
        Master-->>App: Commit 완료
        
        App->>Cache: 16. (선택) 관련 성적 캐시 삭제 (Evict)
        App-->>User: 17. "접수되었습니다."
    end