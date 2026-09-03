output "api_origin_domain" {
  description = "Domínio do ALB para configurar a distribuição CloudFront do frontend."
  value       = aws_lb.api.dns_name
}

output "api_url" {
  description = "URL do origin. O listener exige o cabeçalho privado X-Origin-Token."
  value       = "http://${aws_lb.api.dns_name}"
}

output "origin_token" {
  description = "Token privado compartilhado com o proxy HTTPS do frontend."
  value       = random_password.origin_token.result
  sensitive   = true
}

output "ecr_repository_url" {
  description = "Repositório da imagem do backend."
  value       = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_task_family" {
  value = aws_ecs_task_definition.app.family
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "cloudwatch_log_group" {
  value = aws_cloudwatch_log_group.app.name
}

output "rds_secret_arn" {
  description = "Segredo gerenciado pelo RDS com as credenciais do PostgreSQL."
  value       = aws_db_instance.postgres.master_user_secret[0].secret_arn
  sensitive   = true
}
