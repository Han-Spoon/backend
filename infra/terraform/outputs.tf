output "cloudfront_domain" {
  description = "CloudFront 배포 도메인"
  value       = aws_cloudfront_distribution.main.domain_name
}

output "origin_eip" {
  description = "EC2 오리진 고정 IP"
  value       = aws_eip.origin.public_ip
}

output "rds_endpoint" {
  description = "RDS 엔드포인트 (application-prod.yml 의 DB_HOST)"
  value       = aws_db_instance.main.address
}

output "ecr_repository_urls" {
  description = "GitHub Actions 가 push 할 대상"
  value       = { for k, v in aws_ecr_repository.app : k => v.repository_url }
}

output "github_actions_role_arn" {
  description = "워크플로의 role-to-assume 에 넣을 값"
  value       = aws_iam_role.github_actions.arn
}

output "s3_bucket_name" {
  description = "이미지 버킷"
  value       = aws_s3_bucket.images.bucket
}

output "origin_secret" {
  description = "오리진이 검증할 X-Origin-Secret 헤더 값"
  value       = random_password.origin_secret.result
  sensitive   = true
}
# ── DNS 수동 등록용 (Vercel 대시보드에 입력) ──────────────────
output "acm_validation_record" {
  description = "1단계: Vercel DNS 에 등록할 인증서 검증 CNAME"
  value = {
    for dvo in aws_acm_certificate.cdn.domain_validation_options :
    dvo.domain_name => {
      type  = dvo.resource_record_type
      name  = dvo.resource_record_name
      value = dvo.resource_record_value
    }
  }
}

output "api_dns_record" {
  description = "2단계: Vercel DNS 에 등록할 API CNAME"
  value       = "${local.api_fqdn}  CNAME  ${aws_cloudfront_distribution.main.domain_name}"
}
