terraform {
  required_version = ">= 1.10"

  required_providers {
    aws    = { source = "hashicorp/aws", version = "~> 5.60" }
    random = { source = "hashicorp/random", version = "~> 3.6" }
  }

  backend "s3" {
    bucket       = "hanspoon-tfstate"
    key          = "prod/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.region
  default_tags { tags = local.common_tags }
}

provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
  default_tags { tags = local.common_tags }
}

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name_prefix = "${var.project}-${var.env}"
  api_fqdn    = "${var.api_subdomain}.${var.domain_name}"

  common_tags = {
    Project   = var.project
    Env       = var.env
    ManagedBy = "terraform"
  }

  azs = slice(data.aws_availability_zones.available.names, 0, 2)

  public_subnets = {
    for idx, az in local.azs : az => { cidr = cidrsubnet(var.vpc_cidr, 8, idx), az = az }
  }

  private_subnets = {
    for idx, az in local.azs : az => { cidr = cidrsubnet(var.vpc_cidr, 8, idx + 10), az = az }
  }

  backend_memory = 640
  ai_memory      = 768
}