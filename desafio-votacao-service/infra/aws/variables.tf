variable "aws_region" {
  description = "Região AWS da implantação."
  type        = string
  default     = "us-east-2"
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

variable "vpc_cidr" {
  description = "CIDR da VPC."
  type        = string
  default     = "10.42.0.0/16"
}

variable "api_allowed_cidrs" {
  description = "Redes adicionais que podem alcançar o ALB para diagnóstico."
  type        = list(string)
  default     = []
}

variable "container_cpu" {
  description = "CPU da tarefa ECS em unidades do Fargate."
  type        = number
  default     = 512
}

variable "container_memory" {
  description = "Memória da tarefa ECS em MiB."
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "Quantidade inicial de tarefas."
  type        = number
  default     = 1
}

variable "database_instance_class" {
  description = "Classe da instância RDS."
  type        = string
  default     = "db.t4g.micro"
}

variable "database_multi_az" {
  description = "Habilita réplica síncrona do RDS em outra zona."
  type        = bool
  default     = false
}

variable "protect_data" {
  description = "Habilita proteção contra exclusão no RDS e preserva imagens do ECR."
  type        = bool
  default     = false
}

variable "public_base_url" {
  description = "URL pública usada nos callbacks mobile. Vazio usa o endereço HTTP do ALB."
  type        = string
  default     = ""
}

variable "jwt_issuer" {
  description = "Issuer gravado e validado nos JWTs."
  type        = string
  default     = "urn:desafio-votacao-service"
}

variable "cpf_fake_mode" {
  description = "Modo do client fake de CPF: aleatorio, apto ou inapto."
  type        = string
  default     = "aleatorio"

  validation {
    condition     = contains(["aleatorio", "apto", "inapto"], var.cpf_fake_mode)
    error_message = "cpf_fake_mode deve ser aleatorio, apto ou inapto."
  }
}

variable "dynatrace_metrics_uri" {
  description = "Endpoint de ingestão de métricas. Nulo mantém o perfil Dynatrace desabilitado."
  type        = string
  default     = null
  nullable    = true
}

variable "dynatrace_api_token_secret_arn" {
  description = "ARN de um segredo com o token metrics.ingest do Dynatrace."
  type        = string
  default     = null
  nullable    = true
}