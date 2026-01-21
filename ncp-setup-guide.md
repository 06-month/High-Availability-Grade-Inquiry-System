# NCP 환경 설정 가이드

## 1. 생성된 리소스 정보 확인

### MySQL 정보 확인
```bash
# NCP 콘솔에서 확인할 정보들
MYSQL_MASTER_HOST=your-mysql-master-host.ncloud.com
MYSQL_REPLICA_HOST=your-mysql-replica-host.ncloud.com
MYSQL_PORT=3306
MYSQL_DATABASE=grade_portal
MYSQL_USERNAME=grade_user
MYSQL_PASSWORD=your-mysql-password
```

### Redis Cluster 정보 확인
```bash
# NCP 콘솔에서 Redis 클러스터의 각 노드 정보 확인
REDIS_NODE1_HOST=your-redis-node1.ncloud.com
REDIS_NODE1_PORT=6379
REDIS_NODE2_HOST=your-redis-node2.ncloud.com  
REDIS_NODE2_PORT=6379
REDIS_NODE3_HOST=your-redis-node3.ncloud.com
REDIS_NODE3_PORT=6379
REDIS_PASSWORD=your-redis-password
```

### Container Registry 정보
```bash
REGISTRY_URL=your-registry.kr.ncr.ntruss.com
```

## 2. 데이터베이스 스키마 초기화

MySQL Master에 스키마를 적용해야 합니다:

```bash
# MySQL 클라이언트로 접속
mysql -h your-mysql-master-host.ncloud.com -u grade_user -p grade_portal

# 또는 schema.sql 파일 직접 실행
mysql -h your-mysql-master-host.ncloud.com -u grade_user -p grade_portal < schema.sql
```

## 3. Kubernetes Secret 생성

```bash
# NKS 클러스터에 접속 후
kubectl create namespace grade-inquiry

kubectl create secret generic grade-inquiry-secret \
  --namespace=grade-inquiry \
  --from-literal=mysql-master-host=your-mysql-master-host.ncloud.com \
  --from-literal=mysql-replica-host=your-mysql-replica-host.ncloud.com \
  --from-literal=mysql-username=grade_user \
  --from-literal=mysql-password=your-mysql-password \
  --from-literal=redis-node1-host=your-redis-node1.ncloud.com \
  --from-literal=redis-node2-host=your-redis-node2.ncloud.com \
  --from-literal=redis-node3-host=your-redis-node3.ncloud.com \
  --from-literal=redis-password=your-redis-password \
  --from-literal=jwt-issuer-uri=https://your-auth-server.com/auth/realms/grade-portal
```

## 4. 배포 스크립트 실행

```bash
# Container Registry 로그인
docker login your-registry.kr.ncr.ntruss.com

# 배포 실행
./deploy-nks.sh
```

## 5. 테스트 데이터 삽입

배포 완료 후 테스트용 데이터를 삽입합니다:

```sql
-- 테스트 학생 데이터
INSERT INTO STUDENTS (student_id, name, department, grade_level) VALUES
('20240001', '김철수', '컴퓨터공학과', 3),
('20240002', '이영희', '전자공학과', 2);

-- 테스트 과목 데이터  
INSERT INTO COURSES (course_id, course_name, credits, department) VALUES
('CS101', '프로그래밍기초', 3, '컴퓨터공학과'),
('CS201', '자료구조', 3, '컴퓨터공학과');

-- 테스트 수강 데이터
INSERT INTO ENROLLMENTS (enrollment_id, student_id, course_id, semester, instructor) VALUES
(1, '20240001', 'CS101', '2024-1', '박교수'),
(2, '20240001', 'CS201', '2024-1', '김교수');

-- 테스트 성적 데이터
INSERT INTO GRADE_DETAILS (detail_id, enrollment_id, assignment_type, score, max_score, weight) VALUES
(1, 1, 'MIDTERM', 85, 100, 0.3),
(2, 1, 'FINAL', 90, 100, 0.4),
(3, 1, 'ASSIGNMENT', 95, 100, 0.3);

-- 성적 요약 데이터
INSERT INTO GRADE_SUMMARY (summary_id, student_id, semester, total_credits, gpa, rank_in_department) VALUES
(1, '20240001', '2024-1', 6, 4.2, 5);

-- 성적 공개 정책
INSERT INTO GRADE_RELEASE_POLICY (policy_id, semester, release_date, is_released) VALUES
(1, '2024-1', '2024-06-20 09:00:00', true);
```

## 6. API 테스트

```bash
# 서비스 외부 IP 확인
kubectl get services -n grade-inquiry

# 헬스체크
curl http://EXTERNAL-IP/actuator/health

# 성적 조회 테스트 (JWT 토큰 필요)
curl -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  "http://EXTERNAL-IP/api/grades/summary?studentId=20240001&semester=2024-1"
```

## 7. 모니터링 확인

```bash
# Pod 상태 확인
kubectl get pods -n grade-inquiry

# 로그 확인
kubectl logs -f deployment/grade-inquiry-was -n grade-inquiry

# 메트릭 확인
curl http://EXTERNAL-IP/actuator/prometheus
```