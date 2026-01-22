# Kubernetes 배포 가이드

## 📋 사전 준비

1. **Kubernetes 클러스터 접근 권한 확인**
   ```bash
   kubectl cluster-info
   kubectl get nodes
   ```

2. **Container Registry 설정**
   - `k8s/was-deployment.yaml`과 `k8s/web-deployment.yaml`의 이미지 경로를 실제 레지스트리로 변경
   - 예: `your-registry.ncloud.com` → 실제 레지스트리 주소

3. **Secret 값 업데이트**
   - `k8s/secret.yaml`의 Base64 인코딩된 값을 실제 값으로 변경
   ```bash
   # Base64 인코딩 방법
   echo -n "실제값" | base64
   ```

## 🚀 배포 순서

### 1. 이미지 빌드 및 푸시

```bash
# 이미지 빌드
./build-images.sh

# 이미지 푸시 (레지스트리 로그인 후)
docker login your-registry.ncloud.com
docker push your-registry.ncloud.com/grade-inquiry-was:v1.0.0
docker push your-registry.ncloud.com/grade-inquiry-web:v1.0.0
```

### 2. Kubernetes 리소스 배포

```bash
# 자동 배포 스크립트 사용
./deploy-nks.sh

# 또는 수동 배포
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/was-deployment.yaml
kubectl apply -f k8s/web-deployment.yaml
kubectl apply -f k8s/services.yaml
kubectl apply -f k8s/hpa.yaml
```

### 3. 배포 상태 확인

```bash
# Pod 상태 확인
kubectl get pods -n grade-inquiry

# 서비스 상태 확인
kubectl get services -n grade-inquiry

# LoadBalancer IP 확인
kubectl get service grade-inquiry-lb -n grade-inquiry

# HPA 상태 확인
kubectl get hpa -n grade-inquiry
```

## 🔍 주요 명령어

### 로그 확인
```bash
# WAS Pod 로그
kubectl logs -f deployment/grade-was -n grade-inquiry

# Web Pod 로그
kubectl logs -f deployment/grade-web -n grade-inquiry
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

## ⚠️ 주의사항

1. **Secret 값 변경**: 배포 전 `k8s/secret.yaml`의 모든 값을 실제 값으로 변경해야 합니다.
2. **이미지 경로**: Deployment 파일의 이미지 경로를 실제 레지스트리로 변경해야 합니다.
3. **데이터베이스 연결**: MySQL Master/Slave 호스트 정보를 올바르게 설정해야 합니다.
4. **리소스 제한**: 클러스터 리소스에 맞게 조정이 필요할 수 있습니다.
