variable "project" {
  type    = string
  default = "hanspoon"
}

variable "env" {
  type    = string
  default = "prod"
  validation {
    condition     = contains(["dev", "prod"], var.env)
    error_message = "env 는 dev 또는 prod 여야 함."
  }
}

variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
  validation {
    condition     = can(cidrhost(var.vpc_cidr, 0))
    error_message = "유효한 CIDR 표기여야 함."
  }
}

variable "domain_name" {
  type    = string
  default = "han-spoon.site"
}

variable "instance_type" {
  type    = string
  default = "t4g.small"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "github_repositories" {
  description = "OIDC 로 배포를 허용할 저장소 목록. 백엔드와 AI 서비스가 같은 태스크 정의를 갱신하므로 역할을 공유한다."
  type        = list(string)
  default = ["Han-Spoon/backend", "Han-Spoon/ai"]
}

variable "alert_email" {
  type    = string
  default = "lys8167@gmail.com"
}

variable "monthly_budget_usd" {
  type    = number
  default = 60
  validation {
    condition     = var.monthly_budget_usd > 0
    error_message = "예산은 0보다 커야 함."
  }
}

variable "api_subdomain" {
  description = "API 호스트. root 도메인은 Vercel 프론트엔드가 쓰고 있음."
  type        = string
  default     = "api"
}

variable "web_origins" {
  description = "S3 presigned 업로드를 수행하는 프론트엔드 origin. S3 CORS 허용 목록."
  type        = list(string)
  default = [
    "https://han-spoon.site",
    "https://www.han-spoon.site",
    "http://localhost:5173",
  ]
}
