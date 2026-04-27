#!/bin/bash
set -e

CURRENT=$(grep -oP 'http://\K(blue|green)' /home/ubuntu/nginx/conf.d/service-url.inc 2>/dev/null || echo blue)

if [ "$CURRENT" = "blue" ]; then
  TARGET=green
  PORT=8082
else
  TARGET=blue
  PORT=8081
fi

echo "Current: $CURRENT, Target: $TARGET (port $PORT)"

# 다음 훅 스크립트에서 사용할 변수 저장
echo "CURRENT=$CURRENT" > /home/ubuntu/deploy-state.env
echo "TARGET=$TARGET" >> /home/ubuntu/deploy-state.env
echo "PORT=$PORT" >> /home/ubuntu/deploy-state.env

# 배포 대상 컨테이너 정리
docker stop $TARGET || true
docker rm $TARGET || true