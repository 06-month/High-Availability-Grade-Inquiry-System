# 데이터베이스 Failover 가이드

## 🔄 자동 Failover 시스템

### 개요
성적 조회 시스템에 **자동 데이터베이스 Failover** 기능이 구현되어 있습니다. Read Replica가 장애 시 자동으로 Master DB로 전환되어 서비스 중단을 최소화합니다.

## 🏗️ Failover 아키텍처

```
[Application] 
    ↓
[FailoverRoutingDataSource]
    ↓
┌─────────────────┬─────────────────┐
│   Read Replica  │   Master DB     │
│   (읽기 우선)    │   (쓰기 + 백업)  │
└─────────────────┴─────────────────┘
```

### 라우팅 로직
- **읽기 전용 트랜잭션**: Read Replica 우선 → 실패 시 Master로 자동 전환
- **쓰기 트랜잭션**: Master DB만 사용

## ⚡ Circuit Breaker 패턴

### 설정값
```java
MAX_FAILURES = 3        // 최대 실패 허용 횟수
RECOVERY_TIME_MS = 30000 // 복구 시도 간격 (30초)
```

### 동작 방식
1. **정상 상태**: Read Replica 사용
2. **실패 감지**: 연결 실패 시 실패 카운트 증가
3. **Circuit Open**: 3회 실패 시 Read Replica 차단
4. **Failover**: Master DB로 자동 전환
5. **복구 시도**: 30초 후 Read Replica 재시도
6. **Circuit Close**: 연결 성공 시 정상 상태 복구

## 📊 Failover 시나리오

### 시나리오 1: Read Replica 일시 장애
```
1. 사용자 성적 조회 요청
2. Read Replica 연결 실패 (1회)
3. 즉시 Master DB로 Failover
4. 성적 데이터 정상 반환
5. 30초 후 Read Replica 복구 시도
```

### 시나리오 2: Read Replica 완전 장애
```
1. Read Replica 3회 연속 실패
2. Circuit Breaker 작동 (Read Replica 차단)
3. 모든 읽기 요청이 Master DB로 라우팅
4. 30초마다 Read Replica 복구 확인
5. 복구 시 자동으로 Read Replica 사용 재개
```

### 시나리오 3: Master DB 장애
```
1. 쓰기 작업 실패 (성적 업데이트 등)
2. 애플리케이션 레벨에서 예외 발생
3. 사용자에게 "일시적 오류" 메시지 표시
4. 읽기 작업은 Read Replica에서 계속 가능
```

## 🔍 모니터링 및 로깅

### 로그 메시지
```bash
# 정상 라우팅
DEBUG: Routing to readReplica

# Failover 발생
WARN: ReadReplica connection failed, trying master: Connection refused
WARN: Datasource readReplica failure count: 1

# Circuit Breaker 작동
ERROR: Datasource readReplica has been marked as unavailable after 3 failures

# 복구 시도
INFO: Attempting to recover datasource: readReplica

# 복구 완료
INFO: Datasource readReplica has been recovered
```

### 메트릭 수집
- **실패 카운트**: 각 데이터소스별 실패 횟수
- **마지막 실패 시간**: Circuit Breaker 복구 시점 계산
- **라우팅 결정**: 어떤 DB로 라우팅되었는지 추적

## 🚨 장애 대응 절차

### 1. Read Replica 장애 감지
```bash
# 로그 확인
kubectl logs -f deployment/grade-was -n grade-inquiry | grep "readReplica"

# 메트릭 확인
curl http://your-app/actuator/metrics/grade.db.query
```

### 2. 장애 복구 확인
```bash
# Circuit Breaker 상태 확인
# 로그에서 "Attempting to recover" 메시지 확인

# 복구 완료 확인
# 로그에서 "has been recovered" 메시지 확인
```

### 3. 수동 개입이 필요한 경우
```bash
# 애플리케이션 재시작 (Circuit Breaker 리셋)
kubectl rollout restart deployment/grade-was -n grade-inquiry

# 데이터베이스 상태 직접 확인
mysql -h read-replica-host -u user -p -e "SELECT 1"
```

## ⚙️ 설정 커스터마이징

### application.yml에서 조정 가능한 값들
```yaml
# 향후 확장 시 추가 가능한 설정들
app:
  database:
    failover:
      max-failures: 3
      recovery-time-ms: 30000
      health-check-interval-ms: 10000
```

## 🎯 성능 영향

### Failover 시 성능 변화
- **정상 시**: Read Replica 사용으로 Master 부하 분산
- **Failover 시**: 모든 읽기가 Master로 집중 (일시적 성능 저하 가능)
- **복구 후**: 정상 부하 분산 상태로 복귀

### 최적화 방안
- **캐시 활용**: Redis 캐시로 DB 부하 최소화
- **Connection Pool**: 적절한 커넥션 풀 크기 설정
- **Timeout 설정**: 빠른 실패 감지를 위한 짧은 타임아웃

## 🔧 운영 팁

### 1. 정기 점검
- Read Replica 상태 모니터링
- 로그에서 Failover 빈도 확인
- 성능 메트릭 추적

### 2. 알림 설정
- Circuit Breaker 작동 시 알림
- 복구 완료 시 알림
- 연속 실패 시 긴급 알림

### 3. 테스트 방법
```bash
# Read Replica 의도적 중단으로 Failover 테스트
# (운영 환경에서는 주의!)

# 1. Read Replica 중단
# 2. 성적 조회 API 호출
# 3. 로그에서 Failover 확인
# 4. Read Replica 복구
# 5. 자동 복구 확인
```

---

**✨ 결론**: 자동 Failover 시스템으로 데이터베이스 장애 시에도 서비스 연속성 보장!