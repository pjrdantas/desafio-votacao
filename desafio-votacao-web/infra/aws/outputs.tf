output "bucket_name" {
  description = "Bucket que recebe o build Angular."
  value       = aws_s3_bucket.web.id
}

output "cloudfront_distribution_id" {
  description = "Distribuição usada na invalidação após cada publicação."
  value       = try(aws_cloudfront_distribution.web[0].id, null)
}

output "application_url" {
  description = "URL pública HTTPS da aplicação."
  value = local.cloudfront_enabled ? (
    "https://${aws_cloudfront_distribution.web[0].domain_name}"
  ) : trimsuffix(aws_lambda_function_url.web[0].function_url, "/")
}

output "hosting_mode" {
  description = "Modo de hospedagem HTTPS ativo."
  value       = var.hosting_mode
}
