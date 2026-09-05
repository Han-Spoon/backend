resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${local.name_prefix}-vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${local.name_prefix}-igw" }
}

# 퍼블릭: ECS 컨테이너 인스턴스.
resource "aws_subnet" "public" {
  for_each = local.public_subnets

  vpc_id                  = aws_vpc.main.id
  cidr_block              = each.value.cidr
  availability_zone       = each.value.az
  map_public_ip_on_launch = true

  tags = { Name = "${local.name_prefix}-public-${each.key}" }
}

# 프라이빗: RDS 전용.
resource "aws_subnet" "private" {
  for_each = local.private_subnets

  vpc_id            = aws_vpc.main.id
  cidr_block        = each.value.cidr
  availability_zone = each.value.az

  tags = { Name = "${local.name_prefix}-private-${each.key}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${local.name_prefix}-rt-public" }
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "${local.name_prefix}-rt-private" }
}

resource "aws_route_table_association" "private" {
  for_each = aws_subnet.private

  subnet_id      = each.value.id
  route_table_id = aws_route_table.private.id
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id, aws_route_table.private.id]

  tags = { Name = "${local.name_prefix}-vpce-s3" }
}

# ── 보안 그룹 ────────────────────────────────────────────────
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "instance" {
  name        = "${local.name_prefix}-sg-instance"
  description = "ECS container instance"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name_prefix}-sg-instance" }
}

resource "aws_vpc_security_group_ingress_rule" "instance_from_cloudfront" {
  security_group_id = aws_security_group.instance.id
  description       = "HTTP(8080) from CloudFront edges only"
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
  ip_protocol       = "tcp"
  from_port         = 8080
  to_port           = 8080
}

# 아웃바운드: CLOVA OCR, LLM API, ECR, SSM 호출용
resource "aws_vpc_security_group_egress_rule" "instance_all" {
  security_group_id = aws_security_group.instance.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-sg-rds"
  description = "PostgreSQL from ECS instances only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name_prefix}-sg-rds" }
}

# SG 를 참조하게 함.
resource "aws_vpc_security_group_ingress_rule" "rds_from_instance" {
  security_group_id            = aws_security_group.rds.id
  referenced_security_group_id = aws_security_group.instance.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
}