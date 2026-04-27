#!/bin/bash
set -e

source /home/ubuntu/deploy-state.env

# SSM Parameter Store에서 값 읽기
IMAGE=$(aws ssm get-parameter --name "/commerce/IMAGE" --query Parameter.Value --output text)
SPRING_DATASOURCE_URL=$(aws ssm get-parameter --name "/commerce/SPRING_DATASOURCE_URL" --query Parameter.Value --output text)
SPRING_DATASOURCE_USERNAME=$(aws ssm get-parameter --name "/commerce/SPRING_DATASOURCE_USERNAME" --query Parameter.Value --output text)
SPRING_DATASOURCE_PASSWORD=$(aws ssm get-parameter --name "/commerce/SPRING_DATASOURCE_PASSWORD" --with-decryption --query Parameter.Value --output text)
SPRING_DATA_REDIS_HOST=$(aws ssm get-parameter --name "/commerce/SPRING_DATA_REDIS_HOST" --query Parameter.Value --output text)
AWS_S3_BUCKET=$(aws ssm get-parameter --name "/commerce/AWS_S3_BUCKET" --query Parameter.Value --output text)
NAVER_CLIENT_ID=$(aws ssm get-parameter --name "/commerce/NAVER_CLIENT_ID" --query Parameter.Value --output text)
NAVER_CLIENT_SECRET=$(aws ssm get-parameter --name "/commerce/NAVER_CLIENT_SECRET" --with-decryption --query Parameter.Value --output text)
TOSS_SECRET_KEY=$(aws ssm get-parameter --name "/commerce/TOSS_SECRET_KEY" --with-decryption --query Parameter.Value --output text)
TOSS_CLIENT_KEY=$(aws ssm get-parameter --name "/commerce/TOSS_CLIENT_KEY" --query Parameter.Value --output text)
SPRING_PROFILES_ACTIVE=$(aws ssm get-parameter --name "/commerce/SPRING_PROFILES_ACTIVE" --query Parameter.Value --output text)

# ECR 로그인
ECR_REGISTRY=$(echo $IMAGE | cut -d'/' -f1)
aws ecr get-login-password --region ap-northeast-2 | docker login --username AWS --password-stdin $ECR_REGISTRY

# 명시적 pull
docker pull $IMAGE

# 새 컨테이너 실행
docker run -d \
  --name $TARGET \
  --restart unless-stopped \
  --network monitoring \
  -p $PORT:8080 \
  -e JAVA_OPTS="-Xms128m -Xmx256m" \
  -e SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE" \
  -e SPRING_DATASOURCE_URL="$SPRING_DATASOURCE_URL" \
  -e SPRING_DATASOURCE_USERNAME="$SPRING_DATASOURCE_USERNAME" \
  -e SPRING_DATASOURCE_PASSWORD="$SPRING_DATASOURCE_PASSWORD" \
  -e SPRING_DATA_REDIS_HOST="$SPRING_DATA_REDIS_HOST" \
  -e AWS_S3_BUCKET="$AWS_S3_BUCKET" \
  -e NAVER_CLIENT_ID="$NAVER_CLIENT_ID" \
  -e NAVER_CLIENT_SECRET="$NAVER_CLIENT_SECRET" \
  -e TOSS_SECRET_KEY="$TOSS_SECRET_KEY" \
  -e TOSS_CLIENT_KEY="$TOSS_CLIENT_KEY" \
  $IMAGE