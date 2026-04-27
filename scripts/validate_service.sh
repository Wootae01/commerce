#!/bin/bash
set -e

source /home/ubuntu/deploy-state.env

# 헬스체크 (최대 240초)
for i in $(seq 1 24); do
  STATUS=$(curl -sf http://localhost:$PORT/actuator/health | grep -c UP || true)
  if [ "$STATUS" -ge 1 ]; then
    echo "Health check passed"
    break
  fi
  if [ $i -eq 24 ]; then
    echo "Health check failed"
    docker stop $TARGET || true
    docker rm $TARGET || true
    exit 1
  fi
  echo "Waiting... ($i/24)"
  sleep 10
done

# nginx 전환
echo "set \$service_url http://$TARGET:8080;" > /home/ubuntu/nginx/conf.d/service-url.inc
docker exec nginx nginx -s reload

# 이전 컨테이너 stop만 (rm 안 함 → 즉시 롤백 가능)
docker stop $CURRENT || true

echo "Deploy complete. Active: $TARGET"