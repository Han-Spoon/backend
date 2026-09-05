# ══ IAM ══════════════════════════════════════════════════════
data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "instance" {
  name               = "${local.name_prefix}-instance-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_role_policy_attachment" "instance_managed" {
  for_each = toset([
    "arn:aws:iam::aws:policy/service-role/AmazonEC2ContainerServiceforEC2Role",
    "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore", # SSH 키 없이 접속
  ])

  role       = aws_iam_role.instance.name
  policy_arn = each.value
}

# 부팅 시 자기 자신에게 EIP 를 붙이기 위한 권한
data "aws_iam_policy_document" "instance_eip" {
  statement {
    actions   = ["ec2:AssociateAddress", "ec2:DescribeAddresses"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "instance_eip" {
  name   = "associate-eip"
  role   = aws_iam_role.instance.id
  policy = data.aws_iam_policy_document.instance_eip.json
}

resource "aws_iam_instance_profile" "instance" {
  name = "${local.name_prefix}-instance-profile"
  role = aws_iam_role.instance.name
}

resource "aws_iam_role" "task_execution" {
  name               = "${local.name_prefix}-task-execution-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "task_execution_managed" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "task_execution_ssm" {
  statement {
    actions = ["ssm:GetParameters"]
    resources = concat(
      [for p in aws_ssm_parameter.app : p.arn],
      [aws_ssm_parameter.db_password_seed.arn],
    )
  }
}

resource "aws_iam_role_policy" "task_execution_ssm" {
  name   = "read-app-secrets"
  role   = aws_iam_role.task_execution.id
  policy = data.aws_iam_policy_document.task_execution_ssm.json
}

# 앱 코드가 쓰는 권한. 액세스 키 불필요.
resource "aws_iam_role" "task" {
  name               = "${local.name_prefix}-task-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "task_s3" {
  statement {
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.images.arn}/*"]
  }
  statement {
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.images.arn]
  }
}

resource "aws_iam_role_policy" "task_s3" {
  name   = "images-bucket-access"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task_s3.json
}

# ── GitHub Actions OIDC ──────────────────────
# GitHub OIDC 공급자는 URL 당 계정에 하나만 존재할 수 있다.
data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

data "aws_iam_policy_document" "github_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [data.aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "github_actions" {
  name               = "${local.name_prefix}-github-actions"
  assume_role_policy = data.aws_iam_policy_document.github_assume.json
}

data "aws_iam_policy_document" "github_deploy" {
  statement {
    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
    ]
    resources = ["*"]
  }

  statement {
    actions   = ["ecs:UpdateService", "ecs:DescribeServices", "ecs:RegisterTaskDefinition"]
    resources = ["*"]
  }

  statement {
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.task_execution.arn, aws_iam_role.task.arn]
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "deploy"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.github_deploy.json
}

# ══ ECR ══════════════════════════════════════════════════════
resource "aws_ecr_repository" "app" {
  for_each = toset(["backend", "ai"])

  name                 = "${local.name_prefix}-${each.key}"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration { scan_on_push = true }
}

resource "aws_ecr_lifecycle_policy" "app" {
  for_each   = aws_ecr_repository.app
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "최근 10개만 보관"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

# ══ ECS 클러스터 + 캐패시티 ═══════════════════════════════════
data "aws_ssm_parameter" "ecs_ami" {
  name = "/aws/service/ecs/optimized-ami/amazon-linux-2023/arm64/recommended/image_id"
}

resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = "disabled" # 비용 절감
  }
}

resource "aws_launch_template" "ecs" {
  name_prefix   = "${local.name_prefix}-lt-"
  image_id      = data.aws_ssm_parameter.ecs_ami.value
  instance_type = var.instance_type

  iam_instance_profile { arn = aws_iam_instance_profile.instance.arn }

  vpc_security_group_ids = [aws_security_group.instance.id]

  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      # ECS 최적화 AL2023 ARM64 AMI 의 루트 스냅샷이 30GB 라 그 아래로는 못 줄인다
      volume_size = 30
      volume_type = "gp3"
      encrypted   = true
    }
  }

  user_data = base64encode(<<-EOT
    #!/bin/bash
    echo "ECS_CLUSTER=${aws_ecs_cluster.main.name}" >> /etc/ecs/ecs.config

    TOKEN=$(curl -sX PUT "http://169.254.169.254/latest/api/token" \
      -H "X-aws-ec2-metadata-token-ttl-seconds: 60")
    IID=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" \
      http://169.254.169.254/latest/meta-data/instance-id)
    aws ec2 associate-address --region ${var.region} \
      --instance-id "$IID" \
      --allocation-id ${aws_eip.origin.id} \
      --allow-reassociation
  EOT
  )

  lifecycle { create_before_destroy = true }
}

resource "aws_autoscaling_group" "ecs" {
  name                = "${local.name_prefix}-asg"
  vpc_zone_identifier = [for s in aws_subnet.public : s.id]

  min_size         = 1
  max_size         = 2
  desired_capacity = 1

  launch_template {
    id      = aws_launch_template.ecs.id
    version = "$Latest"
  }

  protect_from_scale_in = true

  tag {
    key                 = "AmazonECSManaged"
    value               = "true"
    propagate_at_launch = true
  }

  lifecycle {
    ignore_changes = [desired_capacity]
  }
}

# 이 블록이 "런칭 때 Fargate 전환" 의 접점
resource "aws_ecs_capacity_provider" "ec2" {
  name = "${local.name_prefix}-cp-ec2"

  auto_scaling_group_provider {
    auto_scaling_group_arn         = aws_autoscaling_group.ecs.arn
    managed_termination_protection = "ENABLED"

    managed_scaling {
      status          = "ENABLED"
      target_capacity = 100
    }
  }
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name       = aws_ecs_cluster.main.name
  capacity_providers = [aws_ecs_capacity_provider.ec2.name]

  default_capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.ec2.name
    weight            = 100
  }
}

# ══ ECS 태스크 + 서비스 ══════════════════════════════════════
resource "aws_cloudwatch_log_group" "app" {
  for_each = toset(["backend", "ai"])

  name              = "/ecs/${local.name_prefix}/${each.key}"
  retention_in_days = 7 # 기본값은 무기한 — 반드시 지정
}

resource "aws_ecs_task_definition" "app" {
  family = "${local.name_prefix}-app"

  # host 모드: 두 컨테이너가 호스트 네트워크 공유 → backend 가 localhost:8000 으로 ai 호출.
  # awsvpc 는 태스크 ENI 에 퍼블릭 IP 를 못 붙여 NAT($33/월)가 필요해진다.
  # 트레이드오프: 한 인스턴스에 같은 태스크 2개 불가. Fargate 전환 시 awsvpc + NAT 필요.
  network_mode             = "host"
  requires_compatibilities = ["EC2"]
  cpu                      = "1536"
  memory                   = "1408"

  execution_role_arn = aws_iam_role.task_execution.arn
  task_role_arn      = aws_iam_role.task.arn

  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name      = "ai"
      image     = "${aws_ecr_repository.app["ai"].repository_url}:latest"
      essential = true
      memory    = local.ai_memory

      environment = [
        { name = "PORT", value = "8000" },
      ]

      secrets = [
        { name = "OPENAI_API_KEY", valueFrom = aws_ssm_parameter.app["openai_api_key"].arn },
        { name = "CLOVA_OCR_SECRET", valueFrom = aws_ssm_parameter.app["clova_ocr_secret"].arn },
        { name = "CLOVA_OCR_URL", valueFrom = aws_ssm_parameter.app["clova_ocr_url"].arn },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app["ai"].name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "ai"
        }
      }

      # ⚠ 이미지에 curl 이 설치되어 있어야 한다 (Dockerfile 확인)
      healthCheck = {
        command  = ["CMD-SHELL", "curl -f http://localhost:8000/health || exit 1"]
        interval = 30
        timeout  = 5
        retries  = 3
      }
    },
    {
      name      = "backend"
      image     = "${aws_ecr_repository.app["backend"].repository_url}:latest"
      essential = true
      memory    = local.backend_memory

      dependsOn = [{ containerName = "ai", condition = "HEALTHY" }]

      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "SERVER_PORT", value = "8080" },
        { name = "AI_SERVICE_BASE_URL", value = "http://localhost:8000" },
        { name = "S3_BUCKET", value = aws_s3_bucket.images.bucket },
        { name = "AWS_REGION", value = var.region },
        { name = "JAVA_TOOL_OPTIONS", value = "-XX:MaxRAMPercentage=60" },
        { name = "DB_HOST", value = aws_db_instance.main.address },
        { name = "CORS_ALLOWED_ORIGINS", value = "https://${var.domain_name}" },
      ]

      secrets = [
        { name = "DB_PASSWORD", valueFrom = aws_ssm_parameter.app["db_password"].arn },
        { name = "JWT_SECRET", valueFrom = aws_ssm_parameter.app["jwt_secret"].arn },
        { name = "GOOGLE_CLIENT_ID", valueFrom = aws_ssm_parameter.app["google_client_id"].arn },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app["backend"].name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "backend"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 60
      }
    },
  ])
}

resource "aws_ecs_service" "app" {
  name            = "${local.name_prefix}-app"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 1

  # 롤링 업데이트 = 나중에 ALB 를 서비스 재생성 없이 붙일 수 있는 전제
  deployment_controller { type = "ECS" }

  # 인스턴스 1대 + host 모드 → 교체형 배포
  deployment_minimum_healthy_percent = 0
  deployment_maximum_percent         = 100

  capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.ec2.name
    weight            = 100
  }

  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [aws_ecs_cluster_capacity_providers.main]
}

# ══ 예산 알림 ═════════════════════════════════════════════════
resource "aws_budgets_budget" "monthly" {
  name         = "${local.name_prefix}-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.alert_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
  }
}