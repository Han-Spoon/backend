# ══ AI 지식 시드 적재 ═════════════════════════════════════════
# .github/workflows/knowledge-seed.yml 이 ECS 일회성 태스크로 scripts/load_knowledge_seed.py 실행.
# 배포 역할과 분리. GitHub Environment(prod-data-dryrun·prod-data)에서만 가정 가능.

data "aws_caller_identity" "current" {}

locals {
  seed_family     = "${local.name_prefix}-seed-loader"
  seed_repository = "Han-Spoon/backend"
  seed_s3_prefix  = "knowledge-seeds/"
}

resource "aws_ecr_repository" "seed_loader" {
  name                 = local.seed_family
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration { scan_on_push = true }
}

resource "aws_ecr_lifecycle_policy" "seed_loader" {
  repository = aws_ecr_repository.seed_loader.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "최근 5개만 보관"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "seed_loader" {
  name              = "/ecs/${local.seed_family}"
  retention_in_days = 30
}

# DB 비밀번호만 읽는 실행 역할
resource "aws_iam_role" "seed_execution" {
  name               = "${local.seed_family}-execution-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy_attachment" "seed_execution_managed" {
  role       = aws_iam_role.seed_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "seed_execution_ssm" {
  statement {
    actions   = ["ssm:GetParameters"]
    resources = [aws_ssm_parameter.app["db_password"].arn]
  }
}

resource "aws_iam_role_policy" "seed_execution_ssm" {
  name   = "read-db-password"
  role   = aws_iam_role.seed_execution.id
  policy = data.aws_iam_policy_document.seed_execution_ssm.json
}

# 기준 리비전. 이미지는 워크플로가 커밋 SHA 태그로 새 리비전 등록. 태스크 역할 없음(시드는 presigned URL)
resource "aws_ecs_task_definition" "seed_loader" {
  family                   = local.seed_family
  network_mode             = "host"
  requires_compatibilities = ["EC2"]
  cpu                      = "256"
  memory                   = "256"
  execution_role_arn       = aws_iam_role.seed_execution.arn

  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name      = "seed-loader"
      image     = "${aws_ecr_repository.seed_loader.repository_url}:latest"
      essential = true

      environment = [
        { name = "DB_HOST", value = aws_db_instance.main.address },
        { name = "DB_NAME", value = aws_db_instance.main.db_name },
        { name = "DB_USER", value = aws_db_instance.main.username },
      ]

      secrets = [
        { name = "PGPASSWORD", valueFrom = aws_ssm_parameter.app["db_password"].arn },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.seed_loader.name
          "awslogs-region"        = var.region
          "awslogs-stream-prefix" = "seed"
        }
      }
    },
  ])

  lifecycle {
    ignore_changes = [container_definitions]
  }
}

# ── GitHub Actions 시드 역할 ──────────────────
data "aws_iam_policy_document" "github_seed_assume" {
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

    # Environment job 만. 두 Environment 모두 main 브랜치로 제한 필수(GitHub 설정)
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${local.seed_repository}:environment:prod-data-dryrun",
        "repo:${local.seed_repository}:environment:prod-data",
      ]
    }
  }
}

resource "aws_iam_role" "github_seed" {
  name               = "${local.name_prefix}-github-seed"
  assume_role_policy = data.aws_iam_policy_document.github_seed_assume.json
}

data "aws_iam_policy_document" "github_seed" {
  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
      "ecr:BatchGetImage",
      "ecr:DescribeImages",
    ]
    resources = [aws_ecr_repository.seed_loader.arn]
  }

  # 리소스 수준 권한 미지원
  statement {
    actions   = ["ecs:RegisterTaskDefinition", "ecs:DescribeTaskDefinition"]
    resources = ["*"]
  }

  statement {
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.seed_execution.arn]
    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }

  # 시드 태스크 정의만, 이 클러스터에서만
  statement {
    actions   = ["ecs:RunTask"]
    resources = ["arn:aws:ecs:${var.region}:${data.aws_caller_identity.current.account_id}:task-definition/${local.seed_family}:*"]
    condition {
      test     = "ArnEquals"
      variable = "ecs:cluster"
      values   = [aws_ecs_cluster.main.arn]
    }
  }

  statement {
    actions   = ["ecs:DescribeTasks", "ecs:StopTask"]
    resources = ["arn:aws:ecs:${var.region}:${data.aws_caller_identity.current.account_id}:task/${aws_ecs_cluster.main.name}/*"]
  }

  statement {
    actions   = ["logs:GetLogEvents"]
    resources = ["${aws_cloudwatch_log_group.seed_loader.arn}:*"]
  }

  statement {
    actions   = ["s3:PutObject", "s3:GetObject"]
    resources = ["${aws_s3_bucket.images.arn}/${local.seed_s3_prefix}*"]
  }

  statement {
    actions   = ["dynamodb:PutItem", "dynamodb:DeleteItem"]
    resources = [aws_dynamodb_table.deploy_lock.arn]
  }

  # 적재 전 스냅샷. 이름이 seed- 로 시작하는 것만 생성·삭제
  statement {
    actions = ["rds:CreateDBSnapshot"]
    resources = [
      aws_db_instance.main.arn,
      "arn:aws:rds:${var.region}:${data.aws_caller_identity.current.account_id}:snapshot:${local.name_prefix}-seed-*",
    ]
  }

  statement {
    actions   = ["rds:DescribeDBSnapshots", "rds:DeleteDBSnapshot"]
    resources = ["arn:aws:rds:${var.region}:${data.aws_caller_identity.current.account_id}:snapshot:${local.name_prefix}-seed-*"]
  }

  statement {
    actions   = ["rds:DescribeDBSnapshots"]
    resources = [aws_db_instance.main.arn]
  }
}

resource "aws_iam_role_policy" "github_seed" {
  name   = "knowledge-seed"
  role   = aws_iam_role.github_seed.id
  policy = data.aws_iam_policy_document.github_seed.json
}
