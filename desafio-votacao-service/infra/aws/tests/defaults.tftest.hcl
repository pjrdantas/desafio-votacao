mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["us-east-2a", "us-east-2b"]
    }
  }

  mock_resource "aws_db_instance" {
    defaults = {
      address = "database.internal"
      master_user_secret = [{
        secret_arn = "arn:aws:secretsmanager:us-east-2:123456789012:secret:database"
      }]
    }
  }
}

mock_provider "random" {}

run "plano_padrao" {
  command = plan

  assert {
    condition     = aws_db_instance.postgres.publicly_accessible == false
    error_message = "O PostgreSQL não pode ser público."
  }

  assert {
    condition     = aws_db_instance.postgres.backup_retention_period == 1
    error_message = "A retenção de backup deve respeitar o limite da conta gratuita."
  }

  assert {
    condition     = aws_lb_listener.http.default_action[0].type == "fixed-response"
    error_message = "O ALB deve rejeitar chamadas que não vierem do proxy HTTPS."
  }

  assert {
    condition = anytrue([
      for condition in aws_lb_listener_rule.origin_authenticated.condition :
      anytrue([
        for header in condition.http_header :
        header.http_header_name == "X-Origin-Token"
      ])
    ])
    error_message = "O encaminhamento ao backend deve exigir o token privado do origin."
  }

  assert {
    condition     = aws_ecs_task_definition.app.cpu == "512"
    error_message = "A tarefa deve iniciar com 0,5 vCPU."
  }

  assert {
    condition     = aws_ecs_service.app.desired_count == 1
    error_message = "O ambiente de demonstração deve iniciar com uma tarefa."
  }
}
