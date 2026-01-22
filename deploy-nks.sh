#!/bin/bash

# NKS (Naver Kubernetes Service) 배포 스크립트
set -e

echo "🚀 Starting NKS deployment..."

# 환경변수 확인
if [ -z "$REGISTRY" ]; then
    echo "❌ REGISTRY 환경변수가 설정되지 않았습니다."
    echo "예: export REGISTRY=your-registry.kr.ncr.ntruss.com/grade-inquiry"
    exit 1
fi

# 변수 설정
PROJECT_NAME="grade-inquiry"
WEB_IMAGE="${REGISTRY}/web:latest"
WAS_IMAGE="${REGISTRY}/was:latest"

echo "📋 Using registry: $REGISTRY"
echo "📋 WAS Image: $WAS_IMAGE"
echo "📋 Web Image: $WEB_IMAGE"

# Container Registry 로그인 확인
echo "🔐 Checking registry login..."
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker가 실행되지 않았습니다."
    exit 1
fi

# Gradle 빌드
echo "🔨 Building application..."
gradle clean build

# Docker 이미지 빌드 및 푸시
echo "📦 Building and pushing Docker images..."

# WAS 이미지 빌드 및 푸시
echo "Building WAS image..."
docker build -t ${WAS_IMAGE} .
echo "Pushing WAS image..."
docker push ${WAS_IMAGE}

# Web 이미지 빌드 및 푸시
echo "Building Web image..."
docker build -f web/Dockerfile -t ${WEB_IMAGE} .
echo "Pushing Web image..."
docker push ${WEB_IMAGE}

# Kubernetes 매니페스트 업데이트
echo "📝 Updating Kubernetes manifests..."
sed -i.bak "s|your-registry/grade-inquiry-was:latest|${WAS_IMAGE}|g" k8s/was-deployment.yaml
sed -i.bak "s|your-registry/grade-inquiry-web:latest|${WEB_IMAGE}|g" k8s/web-deployment.yaml

# kubectl 연결 확인
echo "🔍 Checking kubectl connection..."
if ! kubectl cluster-info > /dev/null 2>&1; then
    echo "❌ kubectl이 클러스터에 연결되지 않았습니다."
    echo "NKS 클러스터 kubeconfig를 설정해주세요."
    exit 1
fi

# Kubernetes 리소스 배포
echo "🔄 Deploying to NKS..."

# Namespace 생성
kubectl apply -f k8s/namespace.yaml

# ConfigMap 및 Secret 배포 (사전에 생성되어 있어야 함)
if kubectl get secret grade-inquiry-secret -n grade-inquiry > /dev/null 2>&1; then
    echo "✅ Secret already exists"
else
    echo "❌ grade-inquiry-secret이 존재하지 않습니다."
    echo "다음 명령으로 Secret을 생성해주세요:"
    echo "kubectl create secret generic grade-inquiry-secret --namespace=grade-inquiry \\"
    echo "  --from-literal=mysql-master-host=your-host \\"
    echo "  --from-literal=mysql-replica-host=your-replica-host \\"
    echo "  --from-literal=mysql-username=grade_user \\"
    echo "  --from-literal=mysql-password=your-password \\"
    echo "  --from-literal=redis-host=your-redis-host \\"
    echo "  --from-literal=redis-password=your-redis-password"
    exit 1
fi

kubectl apply -f k8s/configmap.yaml

# 서비스 배포
kubectl apply -f k8s/services.yaml

# WAS 배포
kubectl apply -f k8s/was-deployment.yaml

# WAS 배포 완료 대기
echo "⏳ Waiting for WAS deployment to be ready..."
kubectl rollout status deployment/grade-was -n grade-inquiry --timeout=300s

# Web 배포
kubectl apply -f k8s/web-deployment.yaml

# Web 배포 완료 대기
echo "⏳ Waiting for Web deployment to be ready..."
kubectl rollout status deployment/grade-web -n grade-inquiry --timeout=300s

# HPA 배포
kubectl apply -f k8s/hpa.yaml

# 배포 상태 확인
echo "📊 Checking deployment status..."
kubectl get pods -n grade-inquiry
kubectl get services -n grade-inquiry
kubectl get hpa -n grade-inquiry

# LoadBalancer IP 확인
echo "🌐 Getting LoadBalancer IP..."
echo "⏳ Waiting for LoadBalancer IP assignment..."
for i in {1..30}; do
    LB_IP=$(kubectl get service grade-inquiry-lb -n grade-inquiry -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
    if [ -n "$LB_IP" ] && [ "$LB_IP" != "null" ]; then
        echo "✅ LoadBalancer IP: $LB_IP"
        echo "🌐 Application URL: http://$LB_IP"
        echo "🏥 Health Check: http://$LB_IP/actuator/health"
        break
    fi
    echo "Waiting... ($i/30)"
    sleep 10
done

if [ -z "$LB_IP" ] || [ "$LB_IP" = "null" ]; then
    echo "⏳ LoadBalancer IP is still being assigned..."
    echo "Run 'kubectl get service grade-inquiry-lb -n grade-inquiry' to check the IP"
fi

echo "🎉 NKS deployment completed successfully!"
echo ""
echo "📋 Useful commands:"
echo "  kubectl get pods -n grade-inquiry"
echo "  kubectl logs -f deployment/grade-was -n grade-inquiry"
echo "  kubectl logs -f deployment/grade-web -n grade-inquiry"
echo "  kubectl describe hpa -n grade-inquiry"
echo "  kubectl get service grade-inquiry-lb -n grade-inquiry"