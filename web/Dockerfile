# Nginx 기반 이미지
FROM nginx:1.25-alpine

# 기존 Nginx 설정 백업
RUN mv /etc/nginx/conf.d/default.conf /etc/nginx/conf.d/default.conf.bak

# 커스텀 Nginx 설정 파일 복사
COPY web/nginx.conf /etc/nginx/conf.d/default.conf

# 정적 파일 복사 (프론트엔드 파일)
COPY frontend/src /usr/share/nginx/html

# 포트 노출
EXPOSE 80

# 헬스체크 설정
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost/nginx-health || exit 1

# Nginx 실행
CMD ["nginx", "-g", "daemon off;"]
