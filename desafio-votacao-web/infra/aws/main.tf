data "aws_caller_identity" "current" {}

locals {
  name               = "${var.project_name}-${var.environment}"
  bucket_name        = "${local.name}-web-${data.aws_caller_identity.current.account_id}"
  cloudfront_enabled = var.hosting_mode == "cloudfront"
  lambda_enabled     = var.hosting_mode == "lambda_url"
}

resource "aws_s3_bucket" "web" {
  bucket        = local.bucket_name
  force_destroy = var.force_destroy
}

resource "aws_s3_bucket_public_access_block" "web" {
  bucket = aws_s3_bucket.web.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "web" {
  bucket = aws_s3_bucket.web.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_versioning" "web" {
  bucket = aws_s3_bucket.web.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_cloudfront_origin_access_control" "web" {
  name                              = "${local.name}-web"
  description                       = "Acesso privado ao bucket do frontend"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_cache_policy" "static" {
  name        = "${local.name}-static"
  default_ttl = 86400
  max_ttl     = 31536000
  min_ttl     = 0

  parameters_in_cache_key_and_forwarded_to_origin {
    cookies_config { cookie_behavior = "none" }
    headers_config { header_behavior = "none" }
    query_strings_config { query_string_behavior = "none" }
    enable_accept_encoding_brotli = true
    enable_accept_encoding_gzip   = true
  }
}

resource "aws_cloudfront_cache_policy" "api" {
  name        = "${local.name}-api-disabled"
  default_ttl = 0
  max_ttl     = 0
  min_ttl     = 0

  parameters_in_cache_key_and_forwarded_to_origin {
    cookies_config { cookie_behavior = "none" }
    headers_config { header_behavior = "none" }
    query_strings_config { query_string_behavior = "none" }
    enable_accept_encoding_brotli = false
    enable_accept_encoding_gzip   = false
  }
}

resource "aws_cloudfront_origin_request_policy" "api" {
  name = "${local.name}-api"

  cookies_config {
    cookie_behavior = "all"
  }

  headers_config {
    header_behavior = "whitelist"
    headers {
      items = [
        "Accept",
        "Authorization",
        "Content-Type",
        "Origin",
        "Referer",
        "User-Agent",
        "X-Correlation-ID",
        "X-CSRF-TOKEN",
        "X-XSRF-TOKEN"
      ]
    }
  }

  query_strings_config {
    query_string_behavior = "all"
  }
}

resource "aws_cloudfront_response_headers_policy" "security" {
  name = "${local.name}-security"

  security_headers_config {
    content_security_policy {
      content_security_policy = "default-src 'self'; base-uri 'self'; frame-ancestors 'self'; form-action 'self'; object-src 'none'; img-src 'self' data:; font-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'"
      override                = true
    }

    content_type_options { override = true }

    frame_options {
      frame_option = "SAMEORIGIN"
      override     = true
    }

    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }

    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = true
      preload                    = true
      override                   = true
    }
  }
}

resource "aws_cloudfront_function" "spa" {
  name    = "${local.name}-spa"
  runtime = "cloudfront-js-2.0"
  publish = true
  code    = <<-JAVASCRIPT
    function handler(event) {
      var request = event.request;
      var uri = request.uri;
      var backendPrefixes = ['/api/', '/swagger-ui', '/v3/api-docs', '/actuator/health'];

      for (var index = 0; index < backendPrefixes.length; index++) {
        if (uri.indexOf(backendPrefixes[index]) === 0) {
          return request;
        }
      }

      if (uri.endsWith('/')) {
        request.uri = uri + 'index.html';
      } else if (uri.indexOf('.') === -1) {
        request.uri = '/index.html';
      }

      return request;
    }
  JAVASCRIPT
}

resource "aws_wafv2_web_acl" "web" {
  provider = aws.global
  count    = local.cloudfront_enabled && var.enable_waf ? 1 : 0

  name  = "${local.name}-web"
  scope = "CLOUDFRONT"

  default_action {
    allow {}
  }

  rule {
    name     = "auth-rate-limit"
    priority = 1

    action {
      block {}
    }

    statement {
      rate_based_statement {
        aggregate_key_type    = "IP"
        evaluation_window_sec = 300
        limit                 = var.auth_requests_per_five_minutes

        scope_down_statement {
          byte_match_statement {
            positional_constraint = "STARTS_WITH"
            search_string         = "/api/v1/auth/"

            field_to_match {
              uri_path {}
            }

            text_transformation {
              priority = 0
              type     = "NONE"
            }
          }
        }
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.name}-auth-rate-limit"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${local.name}-web"
    sampled_requests_enabled   = true
  }
}
resource "aws_cloudfront_distribution" "web" {
  count               = local.cloudfront_enabled ? 1 : 0
  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"
  price_class         = var.price_class
  http_version        = "http2and3"
  comment             = local.name
  web_acl_id          = var.enable_waf ? aws_wafv2_web_acl.web[0].arn : null

  origin {
    domain_name              = aws_s3_bucket.web.bucket_regional_domain_name
    origin_id                = "web"
    origin_access_control_id = aws_cloudfront_origin_access_control.web.id
  }

  origin {
    domain_name = var.api_origin_domain
    origin_id   = "api"

    custom_header {
      name  = "X-Origin-Token"
      value = var.origin_token
    }

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    target_origin_id           = "web"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD", "OPTIONS"]
    cached_methods             = ["GET", "HEAD", "OPTIONS"]
    cache_policy_id            = aws_cloudfront_cache_policy.static.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
    compress                   = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa.arn
    }
  }

  dynamic "ordered_cache_behavior" {
    for_each = toset(["/api/*", "/swagger-ui*", "/v3/api-docs*", "/actuator/health*"])

    content {
      path_pattern               = ordered_cache_behavior.value
      target_origin_id           = "api"
      viewer_protocol_policy     = "redirect-to-https"
      allowed_methods            = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods             = ["GET", "HEAD", "OPTIONS"]
      cache_policy_id            = aws_cloudfront_cache_policy.api.id
      origin_request_policy_id   = aws_cloudfront_origin_request_policy.api.id
      response_headers_policy_id = aws_cloudfront_response_headers_policy.security.id
      compress                   = true
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
    minimum_protocol_version       = "TLSv1.2_2021"
  }
}

resource "aws_s3_bucket_policy" "web" {
  count  = local.cloudfront_enabled ? 1 : 0
  bucket = aws_s3_bucket.web.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowCloudFrontRead"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.web.arn}/*"
      Condition = {
        StringEquals = {
          "AWS:SourceArn" = aws_cloudfront_distribution.web[0].arn
        }
      }
    }]
  })
}

data "archive_file" "lambda_web" {
  count       = local.lambda_enabled ? 1 : 0
  type        = "zip"
  source_file = "${path.module}/lambda/index.mjs"
  output_path = "${path.module}/.terraform/lambda-web.zip"
}

resource "aws_iam_role" "lambda_web" {
  count = local.lambda_enabled ? 1 : 0
  name  = "${local.name}-web-proxy"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_logs" {
  count      = local.lambda_enabled ? 1 : 0
  role       = aws_iam_role.lambda_web[0].name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "lambda_s3" {
  count = local.lambda_enabled ? 1 : 0
  name  = "read-private-web-bucket"
  role  = aws_iam_role.lambda_web[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject"]
      Resource = "${aws_s3_bucket.web.arn}/*"
    }]
  })
}

resource "aws_lambda_function" "web" {
  count                          = local.lambda_enabled ? 1 : 0
  function_name                  = "${local.name}-web"
  role                           = aws_iam_role.lambda_web[0].arn
  handler                        = "index.handler"
  runtime                        = "nodejs22.x"
  architectures                  = ["arm64"]
  filename                       = data.archive_file.lambda_web[0].output_path
  source_code_hash               = data.archive_file.lambda_web[0].output_base64sha256
  memory_size                    = 256
  timeout                        = 29
  reserved_concurrent_executions = -1

  environment {
    variables = {
      API_ORIGIN_DOMAIN = var.api_origin_domain
      ORIGIN_TOKEN      = var.origin_token
      WEB_BUCKET        = aws_s3_bucket.web.id
    }
  }

  depends_on = [aws_iam_role_policy_attachment.lambda_logs]
}

resource "aws_lambda_function_url" "web" {
  count              = local.lambda_enabled ? 1 : 0
  function_name      = aws_lambda_function.web[0].function_name
  authorization_type = "NONE"
  invoke_mode        = "BUFFERED"
}

resource "aws_lambda_permission" "public_function_url" {
  count                  = local.lambda_enabled ? 1 : 0
  statement_id           = "AllowPublicFunctionUrl"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.web[0].function_name
  principal              = "*"
  function_url_auth_type = "NONE"
}

resource "aws_lambda_permission" "public_invoke" {
  count                    = local.lambda_enabled ? 1 : 0
  statement_id             = "AllowPublicInvokeViaFunctionUrl"
  action                   = "lambda:InvokeFunction"
  function_name            = aws_lambda_function.web[0].function_name
  principal                = "*"
  invoked_via_function_url = true
}
