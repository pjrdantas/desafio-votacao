data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

locals {
  name               = "${var.project_name}-${var.environment}"
  availability_zones = slice(data.aws_availability_zones.available.names, 0, 2)
  dynatrace_enabled  = var.dynatrace_metrics_uri != null && var.dynatrace_api_token_secret_arn != null
  public_base_url    = var.public_base_url != "" ? trimsuffix(var.public_base_url, "/") : "http://${aws_lb.api.dns_name}"
  secret_arns = compact([
    aws_db_instance.postgres.master_user_secret[0].secret_arn,
    var.dynatrace_api_token_secret_arn
  ])
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags                 = { Name = local.name }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = local.name }
}

resource "aws_subnet" "public" {
  for_each = { for index, zone in local.availability_zones : zone => index }

  vpc_id                  = aws_vpc.main.id
  availability_zone       = each.key
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, each.value)
  map_public_ip_on_launch = true
  tags                    = { Name = "${local.name}-public-${each.key}" }
}

resource "aws_subnet" "database" {
  for_each = { for index, zone in local.availability_zones : zone => index }

  vpc_id            = aws_vpc.main.id
  availability_zone = each.key
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, each.value + 10)
  tags              = { Name = "${local.name}-database-${each.key}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${local.name}-public" }
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "alb" {
  name        = "${local.name}-alb"
  description = "Entrada HTTP da API"
  vpc_id      = aws_vpc.main.id


  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}


resource "aws_vpc_security_group_ingress_rule" "alb_cloudfront" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTP a partir das origens do CloudFront"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
}

resource "aws_vpc_security_group_ingress_rule" "alb_diagnostic" {
  for_each = toset(var.api_allowed_cidrs)

  security_group_id = aws_security_group.alb.id
  description       = "Acesso HTTP temporário para diagnóstico"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  cidr_ipv4         = each.value
}
resource "aws_security_group" "service" {
  name        = "${local.name}-service"
  description = "Tarefas ECS da API"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Aplicação a partir do ALB"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "database" {
  name        = "${local.name}-database"
  description = "PostgreSQL privado"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL a partir da API"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.service.id]
  }
}

resource "aws_security_group" "efs" {
  name        = "${local.name}-efs"
  description = "Chave JWT persistente"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "NFS a partir da API"
    from_port       = 2049
    to_port         = 2049
    protocol        = "tcp"
    security_groups = [aws_security_group.service.id]
  }
}

resource "aws_db_subnet_group" "postgres" {
  name       = local.name
  subnet_ids = [for subnet in aws_subnet.database : subnet.id]
}

resource "aws_db_instance" "postgres" {
  identifier                  = local.name
  engine                      = "postgres"
  engine_version              = "17"
  instance_class              = var.database_instance_class
  allocated_storage           = 20
  max_allocated_storage       = 50
  storage_type                = "gp3"
  storage_encrypted           = true
  db_name                     = "votacao"
  username                    = "votacao_admin"
  manage_master_user_password = true
  port                        = 5432
  multi_az                    = var.database_multi_az
  publicly_accessible         = false
  db_subnet_group_name        = aws_db_subnet_group.postgres.name
  vpc_security_group_ids      = [aws_security_group.database.id]
  backup_retention_period     = 7
  deletion_protection         = var.protect_data
  skip_final_snapshot         = !var.protect_data
  apply_immediately           = true
  tags                        = { Name = local.name }
}

resource "aws_efs_file_system" "jwt" {
  encrypted        = true
  creation_token   = "${local.name}-jwt"
  performance_mode = "generalPurpose"
  throughput_mode  = "bursting"

  lifecycle_policy {
    transition_to_ia = "AFTER_30_DAYS"
  }

  tags = { Name = "${local.name}-jwt" }
}

resource "aws_efs_mount_target" "jwt" {
  for_each = aws_subnet.database

  file_system_id  = aws_efs_file_system.jwt.id
  subnet_id       = each.value.id
  security_groups = [aws_security_group.efs.id]
}

resource "aws_efs_access_point" "jwt" {
  file_system_id = aws_efs_file_system.jwt.id

  posix_user {
    uid = 10001
    gid = 10001
  }

  root_directory {
    path = "/jwt"

    creation_info {
      owner_uid   = 10001
      owner_gid   = 10001
      permissions = "0700"
    }
  }
}

resource "aws_ecr_repository" "app" {
  name                 = "${local.name}-service"
  image_tag_mutability = "MUTABLE"
  force_delete         = !var.protect_data

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Manter as 20 imagens mais recentes"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 20
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.name}-service"
  retention_in_days = 30
}

resource "aws_iam_role" "execution" {
  name = "${local.name}-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "execution_secrets" {
  name = "read-runtime-secrets"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = local.secret_arns
    }]
  })
}

resource "aws_iam_role" "task" {
  name = "${local.name}-task"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "task_efs" {
  name = "mount-jwt-key-store"
  role = aws_iam_role.task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["elasticfilesystem:ClientMount", "elasticfilesystem:ClientWrite"]
      Resource = aws_efs_file_system.jwt.arn
      Condition = {
        StringEquals = {
          "elasticfilesystem:AccessPointArn" = aws_efs_access_point.jwt.arn
        }
      }
    }]
  })
}

resource "aws_lb" "api" {
  name               = substr("${local.name}-api", 0, 32)
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = [for subnet in aws_subnet.public : subnet.id]
}

resource "aws_lb_target_group" "api" {
  name        = substr("${local.name}-api", 0, 32)
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id

  health_check {
    enabled             = true
    path                = "/actuator/health/readiness"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.api.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}

resource "aws_ecs_cluster" "main" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}
resource "aws_ecs_task_definition" "app" {
  family                   = "${local.name}-service"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.container_cpu
  memory                   = var.container_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  volume {
    name = "jwt-keys"

    efs_volume_configuration {
      file_system_id     = aws_efs_file_system.jwt.id
      transit_encryption = "ENABLED"

      authorization_config {
        access_point_id = aws_efs_access_point.jwt.id
        iam             = "ENABLED"
      }
    }
  }

  container_definitions = jsonencode([{
    name      = "service"
    image     = "${aws_ecr_repository.app.repository_url}:latest"
    essential = true
    portMappings = [{
      containerPort = 8080
      hostPort      = 8080
      protocol      = "tcp"
    }]
    mountPoints = [{
      sourceVolume  = "jwt-keys"
      containerPath = "/app/keys"
      readOnly      = false
    }]
    environment = concat([
      { name = "SPRING_PROFILES_ACTIVE", value = local.dynatrace_enabled ? "cloud,dynatrace" : "cloud" },
      { name = "APP_ENV", value = var.environment },
      { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/votacao?sslmode=require" },
      { name = "DB_POOL_SIZE", value = "10" },
      { name = "JWT_KEY_STORE_PATH", value = "/app/keys/jwt.jwk" },
      { name = "JWT_ISSUER", value = var.jwt_issuer },
      { name = "AUTH_COOKIE_SECURE", value = "true" },
      { name = "CPF_FAKE_MODO", value = var.cpf_fake_mode },
      { name = "MOBILE_BASE_URL", value = local.public_base_url },
      { name = "JAVA_TOOL_OPTIONS", value = "-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" }
      ], local.dynatrace_enabled ? [
      { name = "DYNATRACE_METRICS_URI", value = var.dynatrace_metrics_uri }
    ] : [])
    secrets = concat([
      { name = "DB_USERNAME", valueFrom = "${aws_db_instance.postgres.master_user_secret[0].secret_arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${aws_db_instance.postgres.master_user_secret[0].secret_arn}:password::" }
      ], local.dynatrace_enabled ? [
      { name = "DYNATRACE_API_TOKEN", valueFrom = var.dynatrace_api_token_secret_arn }
    ] : [])
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "service"
      }
    }
    healthCheck = {
      command     = ["CMD-SHELL", "wget -q -O /dev/null http://localhost:8080/actuator/health/liveness || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 60
    }
  }])
}

resource "aws_ecs_service" "app" {
  name                              = "${local.name}-service"
  cluster                           = aws_ecs_cluster.main.id
  task_definition                   = aws_ecs_task_definition.app.arn
  desired_count                     = var.desired_count
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 90

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = [for subnet in aws_subnet.public : subnet.id]
    security_groups  = [aws_security_group.service.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "service"
    container_port   = 8080
  }

  depends_on = [
    aws_lb_listener.http,
    aws_efs_mount_target.jwt,
    aws_iam_role_policy.execution_secrets,
    aws_iam_role_policy.task_efs
  ]
}

resource "aws_appautoscaling_target" "service" {
  max_capacity       = 3
  min_capacity       = 1
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.app.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${local.name}-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.service.resource_id
  scalable_dimension = aws_appautoscaling_target.service.scalable_dimension
  service_namespace  = aws_appautoscaling_target.service.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value       = 65
    scale_in_cooldown  = 120
    scale_out_cooldown = 60

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "unhealthy_targets" {
  alarm_name          = "${local.name}-unhealthy-targets"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "UnHealthyHostCount"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Maximum"
  threshold           = 0
  treat_missing_data  = "notBreaching"

  dimensions = {
    LoadBalancer = aws_lb.api.arn_suffix
    TargetGroup  = aws_lb_target_group.api.arn_suffix
  }
}
