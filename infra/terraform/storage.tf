# ── S3: 메뉴 이미지 ──────────────────────────────────────────
resource "random_id" "bucket_suffix" {
  byte_length = 4
}

resource "aws_s3_bucket" "images" {
  bucket = "${local.name_prefix}-images-${random_id.bucket_suffix.hex}"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_versioning" "images" {
  bucket = aws_s3_bucket.images.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_public_access_block" "images" {
  bucket = aws_s3_bucket.images.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  # 스캔 원본은 90일 후 삭제.
  rule {
    id     = "expire-scan-originals"
    status = "Enabled"
    filter { prefix = "scans/" }
    expiration { days = 90 }
  }

  # 대표 메뉴 이미지는 영구 보관, 옛 버전은 정리
  rule {
    id     = "cleanup-old-versions"
    status = "Enabled"
    filter { prefix = "menus/" }
    noncurrent_version_expiration { noncurrent_days = 30 }
  }
}

# ── RDS: PostgreSQL 16 ───────────────────────────────────────
resource "aws_db_subnet_group" "main" {
  name       = "${local.name_prefix}-db-subnet"
  subnet_ids = [for s in aws_subnet.private : s.id]
}

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_db_instance" "main" {
  identifier     = "${local.name_prefix}-db"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.db_instance_class

  allocated_storage     = 20
  max_allocated_storage = 50
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "hanspoon"
  username = "hanspoon_app"
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false
  multi_az               = false # 런칭 시 true 로

  backup_retention_period = 7
  backup_window           = "17:00-18:00"
  maintenance_window      = "sun:18:00-sun:19:00"

  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name_prefix}-db-final"

  performance_insights_enabled = false

  lifecycle {
    prevent_destroy = true
  }
}

# ── SSM ───────────────────────────────────────
locals {
  app_secrets = [
    "db_password",
    "jwt_secret",
    "google_client_id",
    "openai_api_key",
    "clova_ocr_secret",
    "clova_ocr_url",
  ]
}

resource "aws_ssm_parameter" "app" {
  for_each = toset(local.app_secrets)

  name  = "/${local.name_prefix}/${each.key}"
  type  = "SecureString"
  value = "PLACEHOLDER" # 실제 값은 CLI 로 주입

  lifecycle {
    ignore_changes = [value]
  }
}

# Terraform 이 생성한 DB 비밀번호를 옮겨 담을 곳 (최초 1회 참고용)
resource "aws_ssm_parameter" "db_password_seed" {
  name  = "/${local.name_prefix}/db_password_seed"
  type  = "SecureString"
  value = random_password.db.result
}