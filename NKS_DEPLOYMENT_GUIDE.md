# NKS (Naver Kubernetes Service) 배포 가이드

## 🏗️ 아키텍처 개요

```
[Internet] → [NCP LoadBalancer] → [Web Pod (Nginx)] → [WAS Pod (Spring Boot)] → [MySQL Master/Slave + Redis]
```

### 구성 요소
- **Web Pod**: Nginx 기반 정적 파일 서빙 및 리버스 프록시
- **WAS Pod**: Spring Boot 애플리케이션 서버
- **MySQL**: Master(Write) + Slave(Read) 구성
- **Redis**: 캐시 서버
- **NCP LoadBalancer**: 외부 트래픽 분산

## 📋 사전 준비사항

### 1. NCP 리소스 준비
- **NKS 클러스터**: Kubernetes 클러스터 생성
- **Container Registry**: Docker 이미지 저장소
- **Cloud DB for MySQL**: Master/Slave 구성
- **Cloud DB for Redis**: 캐시 서버
- **Load Balancer**: 외부 노출용 (자동 생성)

### 2. 로컬 환경 준비
```bash
# kubectl 설치 및 NKS 클러스터 연결
# Docker 설치
# NCP CLI 설치 (선택사항)
```

### 3. 클러스터 연결 확인
```bash
kubectl cluster-info
kubectl get nodes
```

## 🚀 배포 단계

### 1단계: 소스코드 준비
```bash
git clone <your-repository-url>
cd High-Availability-Grade-Inquiry-System
```

### 2단계: 환경 설정
```bash
# Container Registry 로그인
docker login your-registry.ncloud.com

# 환경변수 설정
export REGISTRY="your-registry.ncloud.com"
```

### 3단계: Secret 설정 업데이트
```bash
# k8s/secret.yaml 파일의 Base64 인코딩된 값들을 실제 값으로 변경
echo -n "your-mysql-username" | base64
echo -n "your-mysql-password" | base64
echo -n "your-redis-password" | base64
echo -n "your-jwt-issuer-uri" | base64
```

### 4단계: 매니페스트 파일 업데이트
```bash
# k8s/was-deployment.yaml에서 실제 호스트 정보 업데이트
# - MYSQL_MASTER_HOST: "your-mysql-master-host.ncloud.com"
# - MYSQL_SLAVE_HOST: "your-mysql-slave-host.ncloud.com"
# - REDIS_HOST: "your-redis-host.ncloud.com"
```

### 5단계: 배포 실행
```bash
# 배포 스크립트 실행 권한 부여
chmod +x deploy-nks.sh

# 배포 실행
./deploy-nks.sh
```

### 6단계: 배포 확인
```bash
# Pod 상태 확인
kubectl get pods -n grade-inquiry

# 서비스 상태 확인
kubectl get services -n grade-inquiry

# HPA 상태 확인
kubectl get hpa -n grade-inquiry

# LoadBalancer IP 확인
kubectl get service grade-inquiry-lb -n grade-inquiry
```

## 🔧 운영 관리

### Pod 관리
```bash
# Pod 목록 조회
kubectl get pods -n grade-inquiry

# Pod 로그 확인
kubectl logs -f deployment/grade-was -n grade-inquiry
kubectl logs -f deployment/grade-web -n grade-inquiry

# Pod 재시작
kubectl rollout restart deployment/grade-was -n grade-inquiry
kubectl rollout restart deployment/grade-web -n grade-inquiry

# Pod 스케일링
kubectl scale deployment grade-was --replicas=5 -n grade-inquiry
```

### 서비스 관리
```bash
# 서비스 상태 확인
kubectl get services -n grade-inquiry

# 엔드포인트 확인
kubectl get endpoints -n grade-inquiry

# 서비스 상세 정보
kubectl describe service grade-was-service -n grade-inquiry
```

### HPA (Auto Scaling) 관리
```bash
# HPA 상태 확인
kubectl get hpa -n grade-inquiry

# HPA 상세 정보
kubectl describe hpa grade-was-hpa -n grade-inquiry

# HPA 이벤트 확인
kubectl get events -n grade-inquiry --field-selector involvedObject.kind=HorizontalPodAutoscaler
```

## 📊 모니터링

### 헬스체크
```bash
# WAS 헬스체크
kubectl exec -it deployment/grade-was -n grade-inquiry -- curl http://localhost:8080/actuator/health

# Web 헬스체크
kubectl exec -it deployment/grade-web -n grade-inquiry -- curl http://localhost/nginx-health
```

### 메트릭스 확인
```bash
# Prometheus 메트릭스
kubectl port-forward service/grade-was-service 8080:8080 -n grade-inquiry
curl http://localhost:8080/actuator/prometheus
```

### 리소스 사용량 확인
```bash
# Pod 리소스 사용량
kubectl top pods -n grade-inquiry

# 노드 리소스 사용량
kubectl top nodes
```

## 🔄 업데이트 배포

### Rolling Update
```bash
# 새 이미지로 업데이트
kubectl set image deployment/grade-was grade-was=your-registry.ncloud.com/grade-inquiry-was:v2.0.0 -n grade-inquiry

# 롤아웃 상태 확인
kubectl rollout status deployment/grade-was -n grade-inquiry

# 롤백 (필요시)
kubectl rollout undo deployment/grade-was -n grade-inquiry
```

### Blue-Green 배포
```bash
# 새 버전 배포 (다른 이름으로)
kubectl apply -f k8s/was-deployment-v2.yaml

# 트래픽 전환 (서비스 셀렉터 변경)
kubectl patch service grade-was-service -n grade-inquiry -p '{"spec":{"selector":{"version":"v2"}}}'

# 이전 버전 제거
kubectl delete deployment grade-was-v1 -n grade-inquiry
```

## 🚨 트러블슈팅

### 일반적인 문제들

1. **Pod가 시작되지 않는 경우**
   ```bash
   kubectl describe pod <pod-name> -n grade-inquiry
   kubectl logs <pod-name> -n grade-inquiry
   ```

2. **데이터베이스 연결 실패**
   ```bash
   # 네트워크 연결 테스트
   kubectl exec -it deployment/grade-was -n grade-inquiry -- nc -zv your-mysql-host.ncloud.com 3306
   ```

3. **Redis 연결 실패**
   ```bash
   # Redis 연결 테스트
   kubectl exec -it deployment/grade-was -n grade-inquiry -- nc -zv your-redis-host.ncloud.com 6379
   ```

4. **LoadBalancer IP가 할당되지 않는 경우**
   ```bash
   kubectl describe service grade-inquiry-lb -n grade-inquiry
   kubectl get events -n grade-inquiry
   ```

### 로그 분석
```bash
# 에러 로그 필터링
kubectl logs deployment/grade-was -n grade-inquiry | grep ERROR

# 특정 시간대 로그
kubectl logs deployment/grade-was -n grade-inquiry --since=1h

# 이전 컨테이너 로그 (재시작된 경우)
kubectl logs deployment/grade-was -n grade-inquiry --previous
```

## 🔒 보안 설정

### Network Policy (선택사항)
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: grade-inquiry-netpol
  namespace: grade-inquiry
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: grade-inquiry
  egress:
  - to: []
```

### RBAC 설정
```bash
# 서비스 어카운트 생성
kubectl create serviceaccount grade-inquiry-sa -n grade-inquiry

# 역할 바인딩
kubectl create rolebinding grade-inquiry-rb --clusterrole=view --serviceaccount=grade-inquiry:grade-inquiry-sa -n grade-inquiry
```

## 📈 성능 최적화

### 리소스 튜닝
```yaml
# WAS Pod 리소스 조정
resources:
  requests:
    memory: "1Gi"
    cpu: "500m"
  limits:
    memory: "2Gi"
    cpu: "1000m"
```

### JVM 튜닝
```yaml
# ConfigMap에서 JAVA_OPTS 조정
JAVA_OPTS: "-Xms1g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

### HPA 튜닝
```yaml
# 더 민감한 스케일링
metrics:
- type: Resource
  resource:
    name: cpu
    target:
      type: Utilization
      averageUtilization: 50  # 더 낮은 임계값
```