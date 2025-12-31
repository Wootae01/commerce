# provider 설정
provider "aws" {
  region = "ap-northeast-2"
}

# VPC 설정
resource "aws_vpc" "main" {
  cidr_block = "10.0.0.0/16"    # vpc ip 대역 설정
  enable_dns_hostnames = true   # dns 해석 기능 킴
  enable_dns_support = true     # dns 이름 자동으로 붙여줌

  tags = {
    Name = "commerce-vpc"
  }
}

# 인터넷 게이트웨이
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name = "commerce-igw"
  }
}

# public 서브넷 1
resource "aws_subnet" "public_1" {
  vpc_id = aws_vpc.main.id                # vpc 설정
  cidr_block = "10.0.3.0/24"              # ip 대역 설정
  availability_zone = "ap-northeast-2a"   # 가용 영역 설정
  map_public_ip_on_launch = true          # 퍼블릭 ipv4 생성

  tags = {
    Name = "commerce-public-1"
  }
}

# public 서브넷 2
resource "aws_subnet" "public_2" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.4.0/24"
  availability_zone       = "ap-northeast-2c"
  map_public_ip_on_launch = true

  tags = {
    Name = "roommate-public-2"
  }
}

# public 라우팅 테이블
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"    # 모든 외부 트래픽을 여기로 보내라
    gateway_id = aws_internet_gateway.main.id  # 외부 트래픽을 igw로 보냄
  }

  tags = {
    Name = "commerce-public-rt"
  }
}

# public 서브넷 라우팅 연결
resource "aws_route_table_association" "public_1" {
  route_table_id = aws_route_table.public.id
  subnet_id = aws_subnet.public_1.id
}

# public 서브넷 라우팅 연결
resource "aws_route_table_association" "public_2" {
  route_table_id = aws_route_table.public.id
  subnet_id = aws_subnet.public_2.id
}

# 보안 그룹 EC2
resource "aws_security_group" "ec2" {
  name = "commerce-ec2-sg"
  description = "Security group"
  vpc_id = aws_vpc.main.id

  # Spring boot Application
  ingress {
    from_port = 8080
    to_port = 8080
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Allow Spring boot Application"
  }

  # SSH 접속
  ingress {
    from_port = 22
    to_port = 22
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Prometheus
  ingress {
    from_port = 9090
    to_port = 9090
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Grafana
  ingress {
    from_port = 3000
    to_port = 3000
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "All all outbound"
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "commerce-ec2-sg"
  }
}

# IAM 역할 생성
resource "aws_iam_role" "ec2_role" {
  name = "commerce-ec2-role"
  assume_role_policy = jsonencode({
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"  # ec2 만 해당 역할 사용 가능
        }
      }
    ]
  })
}

# ssm 정책 연결
resource "aws_iam_role_policy_attachment" "ssm_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
  role       = aws_iam_role.ec2_role.name
}

# ECR 정책 추가
resource "aws_iam_role_policy" "ecr_policy" {
  name = "ecr-policy"
  role = aws_iam_role.ec2_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken",          # docker 로그인 시 필요한 인증 토큰 받는 권한
          "ecr:BatchCheckLayerAvailability",    # 이미지 레이어가 레지스트리에 있는지 확인
          "ecr:GetDownloadUrlForLayer",         # 특정 레이어를 다운로드할 URL 받기
          "ecr:BatchGetImage"                   # 이미지 매니패스트 가져오기
        ]
        Resource = "*"
      }
    ]
  })
}

# EC2 인스턴스 프로파일 (프로파일 : ROLE을 EC2에 끼우는 어댑터)
resource "aws_iam_instance_profile" "ec2_profile" {
  name = "roommate-ec2-profile"
  role = aws_iam_role.ec2_role.name
}

# EC2 인스턴스
resource "aws_instance" "app" {
  ami           = "ami-0e9bfdb247cc8de84"  # Ubuntu 22.04 LTS AMI
  instance_type = "t2.micro"
  subnet_id = aws_subnet.public_1.id

  # 세부 모니터링 활성화
  monitoring = true

  vpc_security_group_ids = [aws_security_group.ec2.id]
  key_name = "commerce-key"
  iam_instance_profile = aws_iam_instance_profile.ec2_profile.name

  # 루트 디스크 설정 (EBS 볼륨)
  root_block_device {
    volume_size = 30
    volume_type = "gp3"
  }

  user_data = <<-EOF
              #!/bin/bash
              set -e

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

              # CloudWatch Agent 설치 및 설정
              sudo wget https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/amd64/latest/amazon-cloudwatch-agent.deb
              sudo dpkg -i amazon-cloudwatch-agent.deb

              # CloudWatch Agent 설정
              sudo mkdir -p /opt/aws/amazon-cloudwatch-agent/etc/
              sudo bash -c 'cat > /opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json' << 'EOT'
              {
                "agent": {
                  "metrics_collection_interval": 60,
                  "run_as_user": "root"
                },
                "metrics": {
                  "append_dimensions": {
                    "InstanceId": "$${aws:InstanceId}",
                    "InstanceType": "$${aws:InstanceType}",
                    "AutoScalingGroupName": "$${aws:AutoScalingGroupName}"
                  },
                  "metrics_collected": {
                    "mem": {
                      "measurement": [
                        "mem_used_percent"
                      ],
                      "metrics_collection_interval": 60
                    },
                    "swap": {
                      "measurement": [
                        "swap_used_percent"
                      ]
                    }
                  }
                }
              }
              EOT

              # CloudWatch Agent 시작
              sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -s -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
              sudo systemctl start amazon-cloudwatch-agent
              sudo systemctl enable amazon-cloudwatch-agent
              EOF

  tags = {
    Name = "commerce-app"
  }
}

# 탄력적 IP
resource "aws_eip" "app" {
  instance = aws_instance.app.id

  tags = {
    Name = "commerce-app-eip"
  }
}

# 출력값
output "public_ip" {
  value = aws_eip.app.public_ip
}

output "ssh_command" {
  value = "ssh -i commerce-key.pem ubuntu@${aws_eip.app.public_ip}"
}

output "instance_id" {
  value = aws_instance.app.id
  description = "EC2 인스턴스 ID"
}

# ECR 저장소 생성
resource "aws_ecr_repository" "app" {
  name = "commerce-ecr"
  force_delete = true

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = "commerce-ecr"
  }
}

# ECR 저장소 정책
resource "aws_ecr_repository_policy" "app_policy" {
  repository = aws_ecr_repository.app.name
  policy     = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowPushPull"
        Effect = "Allow"
        Principal = {
          AWS = "*"
        }
        Action = [
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:BatchCheckLayerAvailability",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload"
        ]
      }
    ]
  })
}

# 출력값
output "ecr_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

# 키 페어 생성 (ssh 접속할 때 사용)
resource "aws_key_pair" "commerce" {
  key_name = "commerce-key"
  public_key = file("${path.module}/commerce-key.pub")
}

# RDS 보안 그룹
resource "aws_security_group" "rds" {
  name = "commerce-rds-sg"
  description = "Security group for rds"
  vpc_id = aws_vpc.main.id

  # 외부에서 MySQL 접속 허용
  ingress {
    from_port = 3306
    to_port = 3306
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "commerce-rds-sg"
  }
}

# RDS 서브넷 그룹
resource "aws_db_subnet_group" "rds" {
  name = "commerce-rds-subnet-group"
  subnet_ids = [aws_subnet.public_1.id ,aws_subnet.public_2.id]

  tags = {
    Name = "commerce-rds-subnet-group"
  }
}

# RDS 향상된 모니터링 위한 IAM 역할
resource "aws_iam_role" "rds_enhanced_monitoring" {
  name = "commerce-rds-enhanced-monitoring"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "monitoring.rds.amazonaws.com"
        }
      }
    ]
  })
}

# 향상된 모니터링 정책 연결
resource "aws_iam_role_policy_attachment" "rds_enhanced_monitoring" {
  role       = aws_iam_role.rds_enhanced_monitoring.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

# RDS 인스턴스
resource "aws_db_instance" "commerce" {
  identifier = "commerce-db"
  engine = "mysql"
  engine_version = "8.0"
  instance_class = "db.t3.micro"
  allocated_storage = 20
  storage_type = "gp2"

  db_name = "commerce"
  username = "commerce_user"
  password = var.db_password

  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name = aws_db_subnet_group.rds.name

  skip_final_snapshot = true    # 삭제 시 스냅샷 만들지 않음
  publicly_accessible = true

  monitoring_interval = 60
  monitoring_role_arn = aws_iam_role.rds_enhanced_monitoring.arn

  tags = {
    Name = "commerce-db"
  }
}

# RDS 엔드포인트 출력
output "rds_endpoint" {
  value = aws_db_instance.commerce.endpoint
}

# CloudWatch 대시보드 생성
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "portfolio-dashboard"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          view    = "timeSeries"
          stacked = false
          metrics = [
            ["AWS/EC2", "CPUUtilization", "InstanceId", aws_instance.app.id]
          ]
          region = "ap-northeast-2"
          title  = "EC2 CPU 사용률 (%)"
          period = 300
          stat   = "Average"
          yAxis = {
            left = {
              min = 0
              max = 100
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          view    = "timeSeries"
          stacked = false
          metrics = [
            [
              "CWAgent",
              "mem_used_percent",
              "InstanceId", aws_instance.app.id,
              "InstanceType", aws_instance.app.instance_type
            ]
          ]
          region = "ap-northeast-2"
          title  = "EC2 메모리 사용률 (%)"
          period = 300
          stat   = "Average"
          yAxis = {
            left = {
              min = 0
              max = 100
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          view    = "timeSeries"
          stacked = false
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", aws_db_instance.commerce.identifier]
          ]
          region = "ap-northeast-2"
          title  = "RDS CPU 사용률 (%)"
          period = 300
          stat   = "Average"
          yAxis = {
            left = {
              min = 0
              max = 100
            }
          }
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 6
        width  = 12
        height = 6
        properties = {
          view    = "timeSeries"
          stacked = false
          metrics = [
            ["AWS/RDS", "DatabaseConnections", "DBInstanceIdentifier", aws_db_instance.commerce.identifier],
            ["AWS/RDS", "FreeableMemory", "DBInstanceIdentifier", aws_db_instance.commerce.identifier],
            ["AWS/RDS", "FreeStorageSpace", "DBInstanceIdentifier", aws_db_instance.commerce.identifier]
          ]
          region = "ap-northeast-2"
          title  = "RDS 상태"
          period = 300
          stat   = "Average"
        }
      }
    ]
  })
}

# EC2에 CloudWatch Agent 설치를 위한 IAM 정책 추가
resource "aws_iam_role_policy_attachment" "cloudwatch_agent" {
  role       = aws_iam_role.ec2_role.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

# S3
resource "aws_s3_bucket" "app" {
  bucket = "commerce-image-bucket-${random_id.suffix.hex}"
  force_destroy = true

  tags = {
    Name = "commerce-image-bucket"
  }
}

# 버킷 이름 랜덤
resource "random_id" "suffix" {
  byte_length = 4
}

resource "aws_s3_bucket_public_access_block" "bucket-access-block" {
  bucket = aws_s3_bucket.app.id
  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = true
  restrict_public_buckets = true
}

# 기본 암호화(SSE-S3)
resource "aws_s3_bucket_server_side_encryption_configuration" "app" {
  bucket = aws_s3_bucket.app.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# EC2 권한 추가
resource "aws_iam_role_policy" "s3_policy" {
  name = "s3-policy"
  role   = aws_iam_role.ec2_role.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # 버킷 리스트/조회
      {
        Effect = "Allow"
        Action = [
          "s3:ListBucket",
          "s3:GetBucketLocation"
        ]
        Resource = aws_s3_bucket.app.arn
      },
      # 오브젝트 업/다운/삭제
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject"
        ]
        Resource = "${aws_s3_bucket.app.arn}/*"
      }
    ]
  })
}

# S3 버킷명 출력
output "s3_bucket_name" {
  value = aws_s3_bucket.app.bucket
}
