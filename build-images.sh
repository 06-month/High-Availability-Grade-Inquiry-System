#!/bin/bash
set -e

REGISTRY="${REGISTRY:-your-registry.ncloud.com}"
VERSION="${VERSION:-v1.0.0}"

echo "🐳 Building Docker images..."

# 1. Container Registry 로그인 (필요시)
# docker login ${REGISTRY}

# 2. WAS 이미지 빌드 및 푸시
echo "📦 Building WAS image..."
docker build -t grade-inquiry-was:latest -f Dockerfile .
docker tag grade-inquiry-was:latest ${REGISTRY}/grade-inquiry-was:${VERSION}
docker tag grade-inquiry-was:latest ${REGISTRY}/grade-inquiry-was:latest

echo "✅ WAS image built: ${REGISTRY}/grade-inquiry-was:${VERSION}"

# 3. Web 이미지 빌드 및 푸시
echo "📦 Building Web image..."
docker build -t grade-inquiry-web:latest -f web/Dockerfile .
docker tag grade-inquiry-web:latest ${REGISTRY}/grade-inquiry-web:${VERSION}
docker tag grade-inquiry-web:latest ${REGISTRY}/grade-inquiry-web:latest

echo "✅ Web image built: ${REGISTRY}/grade-inquiry-web:${VERSION}"

# 이미지 푸시 (주석 해제하여 사용)
# echo "📤 Pushing images..."
# docker push ${REGISTRY}/grade-inquiry-was:${VERSION}
# docker push ${REGISTRY}/grade-inquiry-was:latest
# docker push ${REGISTRY}/grade-inquiry-web:${VERSION}
# docker push ${REGISTRY}/grade-inquiry-web:latest

echo "✅ All images built successfully!"
echo ""
echo "To push images, run:"
echo "  docker push ${REGISTRY}/grade-inquiry-was:${VERSION}"
echo "  docker push ${REGISTRY}/grade-inquiry-web:${VERSION}"
