output "bucket_name" {
  description = "Bucket que recebe o build Angular."
  value       = aws_s3_bucket.web.id
}

output "cloudfront_distribution_id" {
  description = "Distribuição usada na invalidação após cada publicação."
  value       = aws_cloudfront_distribution.web.id
}

output "application_url" {
  description = "URL pública HTTPS da aplicação."
  value       = "https://${aws_cloudfront_distribution.web.domain_name}"
}