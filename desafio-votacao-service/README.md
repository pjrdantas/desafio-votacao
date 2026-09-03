# desafio-votacao-service

API REST para gerenciamento de pautas, sessões e votos, desenvolvida com Java 21, Spring Boot 4, Maven, PostgreSQL e arquitetura hexagonal.

O enunciado está em [README-original.md](README-original.md).

## Funcionalidades

- Cadastro e consulta de pautas.
- Abertura de uma sessão por pauta, com duração configurável e padrão de um minuto.
- Um voto por associado e pauta, com escolhas `SIM` ou `NAO`.
- Apuração parcial e resultado final.
- Cadastro e login com CPF e senha.
- Autenticação stateless com Bearer JWT.
- Renovação de sessão por cookie HttpOnly e proteção CSRF.
- Client fake para verificar elegibilidade do CPF.
- Contrato JSON para telas `FORMULARIO` e `SELECAO`.
- Tratamento uniforme de erros com correlation ID.
- Swagger/OpenAPI, health checks e métricas para Dynatrace.

## Requisitos

- JDK 21
- Docker Desktop ou Docker Engine com containers Linux
- Maven Wrapper incluído no projeto

## Executar localmente

Inicie somente o PostgreSQL:

```powershell
docker compose up -d postgres
```

Execute a aplicação:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux ou macOS:

```sh
sh mvnw spring-boot:run
```

A API estará disponível em http://localhost:8080.

## Executar com Docker

```powershell
docker compose up --build -d --wait
docker compose ps
```

Para encerrar preservando banco e chave JWT:

```powershell
docker compose down
```

O volume `postgres-data` armazena os dados da aplicação. O volume `jwt-keys` mantém a chave usada para assinar os tokens.

## Eclipse

1. Acesse **File > Import > Maven > Existing Maven Projects**.
2. Selecione a pasta `desafio-votacao-service`.
3. Configure o projeto para usar JDK 21.
4. Atualize as dependências Maven com **Alt+F5**.
5. Execute `br.com.sicredi.desafiovotacao.VotacaoApplication`.

Se a porta 8080 estiver ocupada pelo container, execute `docker compose stop app` antes de iniciar pelo Eclipse.

## Primeiro acesso

Não existem credenciais padrão. Cadastre um usuário pelo frontend ou pelos endpoints de autenticação.

Fluxo HTTP:

1. Faça `GET /api/v1/auth/csrf` e preserve o cookie da resposta.
2. Envie o token retornado no header `X-XSRF-TOKEN`.
3. Cadastre com `POST /api/v1/auth/cadastro`.
4. Entre com `POST /api/v1/auth/login`.
5. Envie o `accessToken` no header `Authorization: Bearer <token>`.

Endpoints:

| Método | Caminho | Descrição |
| --- | --- | --- |
| GET | `/api/v1/auth/csrf` | Obter token CSRF |
| POST | `/api/v1/auth/cadastro` | Cadastrar usuário |
| POST | `/api/v1/auth/login` | Entrar |
| POST | `/api/v1/auth/renovar` | Renovar JWT |
| POST | `/api/v1/auth/logout` | Encerrar sessão |
| GET | `/api/v1/auth/me` | Consultar usuário autenticado |
| GET, POST | `/api/v1/pautas` | Listar ou cadastrar pautas |
| GET | `/api/v1/pautas/{id}` | Consultar pauta |
| POST | `/api/v1/pautas/{id}/sessao` | Abrir sessão |
| POST | `/api/v1/pautas/{id}/votos` | Registrar voto |
| GET | `/api/v1/pautas/{id}/resultado` | Consultar apuração |
| GET, POST | `/api/v1/mobile` | Abrir fluxo mobile |

O corpo do voto contém somente a escolha:

```json
{"escolha":"SIM"}
```

A identidade do associado é obtida do JWT.

## Segurança

- CPF normalizado e validado pelos dígitos verificadores.
- Senha armazenada com BCrypt.
- JWT assinado com RSA e validade padrão de 10 minutos.
- Refresh token aleatório, rotativo e armazenado somente como hash.
- Cookie de renovação com `HttpOnly` e `SameSite=Strict`.
- Logout e reutilização do refresh token imediatamente anterior revogam a sessão.
- Cinco senhas incorretas bloqueiam temporariamente a conta.
- Requisições de autenticação possuem limite por origem.

Para HTTPS, configure `AUTH_COOKIE_SECURE=true`.

## Client fake de CPF

O modo padrão retorna `ABLE_TO_VOTE` ou `UNABLE_TO_VOTE` aleatoriamente durante o registro do voto.

Modos disponíveis:

- `CPF_FAKE_MODO=aleatorio`
- `CPF_FAKE_MODO=apto`
- `CPF_FAKE_MODO=inapto`

Use `apto` para fluxos locais determinísticos.

## Configuração

| Variável | Valor padrão |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5433/votacao` |
| `DATABASE_URL` | Opcional; URI PostgreSQL convertida pelo entrypoint Docker |
| `DB_USERNAME` | `votacao` |
| `DB_PASSWORD` | `votacao` |
| `DB_POOL_SIZE` | `10` |
| `SERVER_PORT` | `8080` |
| `MOBILE_BASE_URL` | `http://localhost:8080` |
| `JWT_ISSUER` | `urn:desafio-votacao-service` |
| `JWT_KEY_STORE_PATH` | `./.secrets/jwt.jwk` |
| `JWT_ACCESS_DURATION` | `10m` |
| `JWT_REFRESH_DURATION` | `8h` |
| `AUTH_COOKIE_SECURE` | `false` |
| `AUTH_RATE_LIMIT` | `120` |
| `CPF_FAKE_MODO` | `aleatorio` |

As opções podem ser definidas no ambiente ou na configuração de execução do Eclipse. O Spring Boot utiliza `src/main/resources/application.yml`. No Docker, `DATABASE_URL` pode fornecer o endpoint PostgreSQL; informe também `DB_USERNAME` e `DB_PASSWORD`. `DB_URL` tem precedência quando definido.

## Swagger e monitoramento

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health
- Liveness: http://localhost:8080/actuator/health/liveness
- Readiness: http://localhost:8080/actuator/health/readiness

O Swagger possui os grupos `v1-autenticacao`, `v1-votacao` e `v1-mobile`. Após o login, informe o JWT no botão **Authorize**.

O perfil `dynatrace` habilita a exportação de métricas e logs estruturados. Configure as variáveis `DYNATRACE_METRICS_URI` e `DYNATRACE_API_TOKEN` antes de ativá-lo.

## Testes

Testes unitários:

```powershell
.\mvnw.cmd test
```

Validação completa com PostgreSQL isolado por Testcontainers:

```powershell
.\mvnw.cmd verify
```

O teste de carga usa um Compose separado e dados próprios:

```powershell
docker compose -f compose.qa.yaml up --build -d --wait
node scripts/carga-local.mjs
docker compose -f compose.qa.yaml down
```

Ele cria 200 mil votos para apuração e executa votos autenticados concorrentes. Não utiliza o banco configurado no Compose principal. O relatório é gravado em target/performance/carga-local.json e não é versionado.

## Arquitetura

```text
src/main/java/br/com/sicredi/desafiovotacao/
├── domain/
├── application/
│   ├── port/in/
│   ├── port/out/
│   └── service/
├── adapter/
│   ├── in/
│   └── out/
└── config/
```

O domínio e os casos de uso não dependem de HTTP, JDBC ou Spring Security. Os adaptadores implementam persistência, segurança, observabilidade e transporte HTTP.

## Operação e investigação de incidentes

Toda resposta da API contém `X-Correlation-ID`; o mesmo identificador é incluído nos logs. No ambiente AWS, comece a investigação pelo health check e pelos eventos do serviço:

```powershell
$cluster = terraform -chdir=infra/aws output -raw ecs_cluster_name
$service = terraform -chdir=infra/aws output -raw ecs_service_name
$logs = terraform -chdir=infra/aws output -raw cloudwatch_log_group
aws ecs describe-services --cluster $cluster --services $service --query "services[0].events[0:10]"
aws logs tail $logs --since 30m --follow
```

Verifique no CloudWatch a CPU e a memória do ECS, targets indisponíveis do ALB e conexões, armazenamento e CPU do RDS. Cada publicação registra uma revisão ECS apontando para a imagem identificada pelo SHA do commit; em caso de regressão, selecione uma revisão anterior da mesma família e atualize o serviço.

## Implantação na AWS

A infraestrutura em `infra/aws` utiliza Terraform e cria:

- repositório ECR com varredura de imagens;
- API no ECS Fargate, atrás de um Application Load Balancer;
- PostgreSQL 17 privado no RDS, com credencial gerenciada pelo Secrets Manager;
- EFS criptografado para compartilhar e preservar a chave de assinatura JWT;
- logs e alarme de targets indisponíveis no CloudWatch;
- autoscaling da API entre uma e três tarefas.

Requisitos adicionais: AWS CLI autenticada, Terraform 1.7 ou superior e Docker em execução. A implantação inicial cria primeiro o ECR, publica a imagem e então provisiona o restante:

```powershell
cd infra/aws
Copy-Item terraform.tfvars.example terraform.tfvars
.\deploy.ps1
```

O Terraform mostra o plano antes de cada aplicação. Ao final, copie os outputs `api_origin_domain` e `origin_token`; eles configuram o proxy HTTPS do frontend. O token é sensível, gerado aleatoriamente e não deve ser versionado. O listener do ALB devolve 403 quando o cabeçalho privado do proxy não confere.

O ambiente de demonstração evita o custo de um NAT Gateway: as tarefas recebem IP público, mas aceitam entrada somente do ALB; o ALB encaminha ao target apenas as chamadas autenticadas pelo proxy HTTPS. PostgreSQL e EFS permanecem em sub-redes sem rota pública. Em um ambiente permanente, use sub-redes privadas com VPC endpoints ou NAT, TLS também entre o proxy e o ALB, `database_multi_az=true`, `protect_data=true` e estado remoto criptografado do Terraform.

Para remover os recursos de demonstração:

```powershell
terraform destroy
```

O workflow `.github/workflows/deploy-aws.yml` executa testes, publica uma imagem identificada pelo commit e atualiza o ECS mediante acionamento manual. Configure no repositório as variáveis `AWS_ROLE_ARN`, `AWS_REGION`, `ECR_REPOSITORY_URL`, `ECS_CLUSTER`, `ECS_SERVICE` e `ECS_TASK_FAMILY`. A autenticação usa OIDC e dispensa chaves AWS permanentes no GitHub.

Para habilitar o Dynatrace, grave o token `metrics.ingest` no Secrets Manager e preencha `dynatrace_metrics_uri` e `dynatrace_api_token_secret_arn` no `terraform.tfvars`.

## Licença

Consulte [LICENSE](LICENSE).
