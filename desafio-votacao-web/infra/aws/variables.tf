variable "aws_region" {
  description = "Região AWS da implantação."
  type        = string
  default     = "sa-east-1"
}

variable "project_name" {
  description = "Prefixo usado nos recursos."
  type        = string
  default     = "desafio-votacao"
}

variable "environment" {
  description = "Nome do ambiente."
  type        = string
  default     = "demo"
}

variable "api_origin_domain" {
  description = "Domínio do ALB do backend, sem protocolo."
  type        = string

  validation {
    condition     = !strcontains(var.api_origin_domain, "://")
    error_message = "Informe somente o domínio retornado por api_origin_domain."
  }
}

variable "force_destroy" {
  description = "Permite apagar o bucket mesmo com arquivos."
  type        = bool
  default     = true
}

variable "price_class" {
  description = "Classe de preço do CloudFront."
  type        = string
  default     = "PriceClass_All"

  validation {
    condition     = contains(["PriceClass_100", "PriceClass_200", "PriceClass_All"], var.price_class)
    error_message = "price_class deve ser PriceClass_100, PriceClass_200 ou PriceClass_All."
  }
}
variable "enable_waf" {
  description = "Protege a distribuição com rate limit global na autenticação."
  type        = bool
  default     = true
}

variable "auth_requests_per_five_minutes" {
  description = "Limite de requisições de autenticação por IP em cinco minutos."
  type        = number
  default     = 300
}