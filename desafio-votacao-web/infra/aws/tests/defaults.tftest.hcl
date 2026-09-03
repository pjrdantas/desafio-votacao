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

run "plano_padrao" {
  command = plan

  variables {
    api_origin_domain = "api.sa-east-1.elb.amazonaws.com"
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
    condition     = aws_cloudfront_distribution.web.viewer_certificate[0].minimum_protocol_version == "TLSv1.2_2021"
    error_message = "O CloudFront deve exigir TLS moderno."
  }
}