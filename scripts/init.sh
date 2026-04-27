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

# CodeDeploy Agent 설치
sudo apt-get install -y ruby-full
wget https://aws-codedeploy-ap-northeast-2.s3.ap-northeast-2.amazonaws.com/latest/install
chmod +x ./install
sudo ./install auto
sudo systemctl start codedeploy-agent
sudo systemctl enable codedeploy-agent