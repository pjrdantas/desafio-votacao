mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "123456789012"
    }
  }
}

mock_provider "aws" {
  alias = "global"
}

mock_provider "archive" {
  mock_data "archive_file" {
    defaults = {
      output_path         = ".terraform/lambda-web.zip"
      output_base64sha256 = "bW9jay1zaGEyNTY="
    }
  }
}

run "plano_padrao" {
  command = plan

  variables {
    api_origin_domain = "api.us-east-2.elb.amazonaws.com"
    origin_token      = "0123456789abcdef0123456789abcdef"
  }

  assert {
    condition     = aws_s3_bucket_public_access_block.web.block_public_policy
    error_message = "O bucket do frontend deve permanecer privado."
  }
  assert {
    condition     = length(aws_wafv2_web_acl.web) == 1
    error_message = "O rate limit global deve estar habilitado por padrão."
  }

  assert {
    condition = (
      !aws_cloudfront_cache_policy.api.parameters_in_cache_key_and_forwarded_to_origin[0].enable_accept_encoding_brotli &&
      !aws_cloudfront_cache_policy.api.parameters_in_cache_key_and_forwarded_to_origin[0].enable_accept_encoding_gzip
    )
    error_message = "A política sem cache da API não pode habilitar codificação na chave."
  }

  assert {
    condition     = aws_cloudfront_distribution.web[0].viewer_certificate[0].minimum_protocol_version == "TLSv1.2_2021"
    error_message = "O CloudFront deve exigir TLS moderno."
  }
}

run "fallback_lambda_url" {
  command = plan

  variables {
    api_origin_domain = "api.us-east-2.elb.amazonaws.com"
    origin_token      = "0123456789abcdef0123456789abcdef"
    hosting_mode      = "lambda_url"
  }

  assert {
    condition     = length(aws_cloudfront_distribution.web) == 0
    error_message = "O fallback não deve tentar criar CloudFront em conta sem verificação."
  }

  assert {
    condition     = length(aws_wafv2_web_acl.web) == 0
    error_message = "O fallback não deve manter um WAF sem associação."
  }

  assert {
    condition     = length(aws_lambda_function_url.web) == 1
    error_message = "O fallback deve publicar uma URL HTTPS da Lambda."
  }
}
