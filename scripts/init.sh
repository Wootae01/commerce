#!/bin/bash
set -e

# 스왑 메모리 1GB 추가 (t3.micro 메모리 부족 대비)
fallocate -l 1G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# SSM Agent 설치
sudo snap install amazon-ssm-agent --classic
sudo systemctl start snap.amazon-ssm-agent.amazon-ssm-agent.service
sudo systemctl enable snap.amazon-ssm-agent.amazon-ssm-agent.service

# AWS CLI 설치
sudo apt-get update
sudo apt-get install -y unzip
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip"
unzip awscliv2.zip
sudo ./aws/install

# Docker 설치 및 실행
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch="$(dpkg --print-architecture)" signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu "$(. /etc/os-release && echo "$VERSION_CODENAME")" stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu
sudo systemctl start docker
sudo systemctl enable docker

# Docker Compose 설치
sudo curl -L "https://github.com/docker/compose/releases/download/v2.20.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Docker 네트워크 생성
docker network create monitoring || true

# CloudWatch Agent 설치 및 설정
sudo wget https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb
sudo dpkg -i amazon-cloudwatch-agent.deb

sudo mkdir -p /opt/aws/amazon-cloudwatch-agent/etc/
sudo bash -c 'cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json' << 'EOT'
{
  "agent": {
    "metrics_collection_interval": 60,
    "run_as_user": "root"
  },
  "metrics": {
    "append_dimensions": {
      "InstanceId": "${aws:InstanceId}",
      "InstanceType": "${aws:InstanceType}",
      "AutoScalingGroupName": "${aws:AutoScalingGroupName}"
    },
    "metrics_collected": {
      "mem": {
        "measurement": ["mem_used_percent"],
        "metrics_collection_interval": 60
      },
      "swap": {
        "measurement": ["swap_used_percent"]
      }
    }
  }
}
EOT

sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -s -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
sudo systemctl start amazon-cloudwatch-agent
sudo systemctl enable amazon-cloudwatch-agent

# nginx 설정 디렉토리 생성
mkdir -p /home/ubuntu/nginx/conf.d

# nginx.conf 작성
cat > /home/ubuntu/nginx/nginx.conf << 'NGINXCONF'
events {}
http {
    server {
        listen 80;
        include /etc/nginx/conf.d/service-url.inc;
        resolver 127.0.0.11 valid=10s;
        location / {
            proxy_pass $service_url;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            add_header X-Active-Server $service_url;
        }
    }
}
NGINXCONF

# service-url.inc 초기값 (blue로 시작)
echo 'set $service_url http://blue:8080;' > /home/ubuntu/nginx/conf.d/service-url.inc

# nginx 컨테이너 실행
docker run -d --name nginx --network monitoring -p 80:80 \
    -v /home/ubuntu/nginx/nginx.conf:/etc/nginx/nginx.conf \
    -v /home/ubuntu/nginx/conf.d:/etc/nginx/conf.d \
    --restart unless-stopped \
    nginx:alpine

# deploy.sh 생성
cat > /home/ubuntu/deploy.sh << 'DEPLOYSH'
#!/bin/bash
set -e

# 인자로 받은 ECR 이미지 주소와 스프링 프로파일
IMAGE=$1
PROFILE=$2

# 현재 nginx가 바라보는 대상(blue/green) 확인 → 파일 없으면 기본값 blue
CURRENT=$(grep -oP 'http://\K(blue|green)' /home/ubuntu/nginx/conf.d/service-url.inc 2>/dev/null || echo blue)
# 현재가 blue면 green에 배포, 그 반대도 동일
if [ "$CURRENT" = "blue" ]; then TARGET=green; PORT=8082; else TARGET=blue; PORT=8081; fi

echo "Deploying to $TARGET (port $PORT), current: $CURRENT"

# 배포 대상 컨테이너가 이미 있으면 제거
docker stop spring-app-$TARGET || true
docker rm spring-app-$TARGET || true

# 새 버전 컨테이너 실행 (환경변수는 SSM 호출 시 주입됨)
docker run -d --name spring-app-$TARGET --network monitoring -p $PORT:8080 -e JAVA_OPTS="-Xms128m -Xmx256m" -e SPRING_PROFILES_ACTIVE=$PROFILE -e SPRING_DATASOURCE_URL="$SPRING_DATASOURCE_URL" -e SPRING_DATASOURCE_USERNAME=$SPRING_DATASOURCE_USERNAME -e SPRING_DATASOURCE_PASSWORD=$SPRING_DATASOURCE_PASSWORD -e SPRING_DATA_REDIS_HOST=$SPRING_DATA_REDIS_HOST -e AWS_S3_BUCKET=$AWS_S3_BUCKET -e NAVER_CLIENT_ID=$NAVER_CLIENT_ID -e NAVER_CLIENT_SECRET=$NAVER_CLIENT_SECRET -e TOSS_SECRET_KEY=$TOSS_SECRET_KEY -e TOSS_CLIENT_KEY=$TOSS_CLIENT_KEY $IMAGE

# 최대 240초(10초 × 24회) 동안 헬스체크 → 실패 시 배포 중단
for i in $(seq 1 24); do
    STATUS=$(curl -sf http://localhost:$PORT/actuator/health | grep -c UP || true)
    if [ "$STATUS" -ge 1 ]; then echo "Health check passed"; break; fi
    if [ $i -eq 24 ]; then echo "Health check failed - rolling back"; exit 1; fi
    echo "Waiting... ($i/24)"
    sleep 10
done

# nginx가 바라보는 대상을 새 버전으로 전환 (무중단)
echo "set \$service_url http://$TARGET:8080;" > /home/ubuntu/nginx/conf.d/service-url.inc
docker exec nginx nginx -s reload

# 이전 버전 컨테이너 종료 및 제거
docker stop spring-app-$CURRENT || true
docker rm spring-app-$CURRENT || true

echo "Deploy complete. Active: $TARGET"
DEPLOYSH

chmod +x /home/ubuntu/deploy.sh