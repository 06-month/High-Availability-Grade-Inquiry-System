#!/bin/bash
set -e

REGISTRY="${REGISTRY:-your-registry.ncloud.com}"
NAMESPACE="grade-inquiry"

echo "🚀 Starting deployment..."

# 네임스페이스 생성
kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -

# 리소스 배포
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/was-deployment.yaml
kubectl apply -f k8s/web-deployment.yaml
kubectl apply -f k8s/services.yaml
kubectl apply -f k8s/hpa.yaml

# 배포 상태 확인
echo "⏳ Waiting for deployments..."
kubectl wait --for=condition=available --timeout=300s deployment/grade-was -n ${NAMESPACE} || true
kubectl wait --for=condition=available --timeout=300s deployment/grade-web -n ${NAMESPACE} || true

# LoadBalancer IP 확인
echo "📊 LoadBalancer IP:"
kubectl get service grade-inquiry-lb -n ${NAMESPACE}

echo "✅ Deployment completed!"
