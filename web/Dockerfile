# Web Pod용 Nginx Dockerfile
FROM nginx:alpine

# 정적 파일 복사
COPY src/main/resources/static/ /usr/share/nginx/html/

# Nginx 설정 복사
COPY web/nginx.conf /etc/nginx/nginx.conf

# 로그 디렉토리 생성
RUN mkdir -p /var/log/nginx

# 헬스체크
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost/nginx-health || exit 1

# 포트 노출
EXPOSE 80

# Nginx 실행
CMD ["nginx", "-g", "daemon off;"]