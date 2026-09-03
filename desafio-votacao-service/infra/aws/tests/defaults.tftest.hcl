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
    condition     = aws_ecs_task_definition.app.cpu == "512"
    error_message = "A tarefa deve iniciar com 0,5 vCPU."
  }

  assert {
    condition     = aws_ecs_service.app.desired_count == 1
    error_message = "O ambiente de demonstração deve iniciar com uma tarefa."
  }
}
