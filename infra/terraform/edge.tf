# ── 오리진 고정 IP ───────────────────────────────────────────
# 인스턴스가 교체돼도 오리진 주소가 안 깨지도록 EIP 를 쓴다.
# 실제 연결은 launch_template 의 user_data 가 부팅 시 수행한다.
resource "aws_eip" "origin" {
  domain = "vpc"
  tags   = { Name = "${local.name_prefix}-eip-origin" }
}

# ── ACM: CloudFront 용은 반드시 us-east-1 ────────────────────
# 루트 도메인(han-spoon.site)은 Vercel 프론트엔드가 쓰므로 api 서브도메인만 발급.
resource "aws_acm_certificate" "cdn" {
  provider          = aws.us_east_1
  domain_name       = local.api_fqdn
  validation_method = "DNS"

  lifecycle { create_before_destroy = true }
}

# DNS 권한이 Vercel 에 있어 검증 레코드는 수동 등록한다.
# → terraform output acm_validation_record 로 값을 확인해 Vercel DNS 에 넣을 것.
# 나중에 Route 53 으로 이관하면 aws_route53_record + aws_acm_certificate_validation 을 되살린다.

# ── CloudFront ───────────────────────────────────────────────
resource "random_password" "origin_secret" {
  length  = 40
  special = false
}

# 하드코딩된 정책 ID 대신 이름으로 조회
data "aws_cloudfront_cache_policy" "disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_cache_policy" "optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_origin_request_policy" "all_viewer" {
  name = "Managed-AllViewer"
}

resource "aws_cloudfront_origin_access_control" "s3" {
  name                              = "${local.name_prefix}-oac-s3"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "main" {
  enabled     = true
  aliases     = [local.api_fqdn]
  price_class = "PriceClass_200" # 아시아 포함, 남미/아프리카 제외

  origin {
    origin_id = "api"

    # Route 53 레코드 대신 EIP 의 퍼블릭 DNS 를 직접 쓴다.
    # EIP 가 고정이므로 인스턴스가 교체돼도 이 이름은 유지된다.
    domain_name = aws_eip.origin.public_dns

    custom_origin_config {
      http_port              = 8080 # backend 컨테이너가 host 모드로 바인딩하는 포트
      https_port             = 443
      origin_protocol_policy = "http-only" # Caddy 로 오리진 TLS 붙이면 https-only
      origin_ssl_protocols   = ["TLSv1.2"]
      origin_read_timeout    = 60 # OCR 파이프라인이 길다 (기본 30s)
    }

    custom_header {
      name  = "X-Origin-Secret"
      value = random_password.origin_secret.result
    }
  }

  origin {
    origin_id                = "images"
    domain_name              = aws_s3_bucket.images.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.s3.id
  }

  default_cache_behavior {
    target_origin_id       = "api"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods         = ["GET", "HEAD"]

    cache_policy_id          = data.aws_cloudfront_cache_policy.disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
  }

  ordered_cache_behavior {
    path_pattern           = "/images/*"
    target_origin_id       = "images"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]

    cache_policy_id = data.aws_cloudfront_cache_policy.optimized.id
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  viewer_certificate {
    # 인증서가 ISSUED 상태여야 배포가 생성된다 → 2단계 apply 필요
    acm_certificate_arn      = aws_acm_certificate.cdn.arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

# S3 버킷은 이 CloudFront 배포만 읽을 수 있다
data "aws_iam_policy_document" "images_cdn" {
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.images.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "images" {
  bucket = aws_s3_bucket.images.id
  policy = data.aws_iam_policy_document.images_cdn.json
}
