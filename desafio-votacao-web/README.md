# desafio-votacao-web

Interface web para gerenciamento de pautas e sessões de votação, desenvolvida com Angular 22 e Angular Material.

## Funcionalidades

- Login e cadastro com validação de CPF.
- Rotas autenticadas e renovação automática da sessão.
- Lista paginada de pautas.
- Cadastro e consulta de pauta.
- Abertura de sessão.
- Voto `Sim` ou `Não` vinculado ao usuário autenticado.
- Contagem regressiva e atualização automática da apuração.
- Resultado parcial e final.
- Renderização do contrato mobile `FORMULARIO` e `SELECAO`.
- Layout responsivo para desktop e celular.

## Requisitos

- Node.js 24.15 ou versão compatível da série 24
- npm
- API disponível em http://localhost:8080 por padrão

## Instalação

```powershell
npm ci
```

No Windows também é possível usar o inicializador do projeto:

```powershell
.\scripts\frontend.cmd install
```

A pasta local `.tools` pode conter uma instalação portátil do Node e não faz parte do repositório.

## Desenvolvimento

```powershell
npm start
```

Ou:

```powershell
.\scripts\frontend.cmd start
```

Acesse http://localhost:4200.

O proxy encaminha `/api` para http://localhost:8080. Para utilizar outra API:

```powershell
$env:API_TARGET = 'http://192.168.0.10:8080'
npm start
```

Reinicie o servidor Angular após alterar `API_TARGET`.

## Docker

```powershell
docker compose up --build -d
```

Variáveis aceitas pelo Compose:

| Variável | Valor padrão | Descrição |
| --- | --- | --- |
| `WEB_PORT` | `4200` | Porta publicada pelo Nginx |
| `API_SCHEME` | `http` | Protocolo da API: `http` ou `https` |
| `API_HOST` | `host.docker.internal` | Hostname da API |
| `API_PORT` | `8080` | Porta da API |

Para encerrar:

```powershell
docker compose down
```

## Primeiro acesso

Abra `/login` e escolha **Criar conta**. Informe nome, CPF válido, senha com pelo menos 10 caracteres e confirmação.

O access token fica somente em memória. A renovação utiliza cookie HttpOnly emitido pela API. Ao recarregar a página, a aplicação tenta restaurar a sessão automaticamente.

## Navegação

| Rota | Descrição |
| --- | --- |
| `/login` | Login e cadastro |
| `/pautas` | Lista de pautas |
| `/pautas/{id}` | Detalhe, sessão, voto e resultado |
| `/mobile` | Interface gerada pelo contrato JSON mobile |

## Testes e build

```powershell
npm test
npm run build
```

No Windows:

```powershell
.\scripts\frontend.cmd test
.\scripts\frontend.cmd build
```

O build de produção é gerado em `dist/desafio-votacao-web/browser`.

## Organização

```text
src/app/
├── auth/
├── core/
├── mobile/
├── pautas/
└── shared/
```

- `auth`: login, CPF, sessão, interceptor e guarda de rotas.
- `core`: cliente HTTP, modelos e tratamento de erros.
- `mobile`: renderizador do contrato mobile.
- `pautas`: listagem, detalhe, sessão, voto e resultado.
- `shared`: componentes reutilizáveis.

## Implantação na AWS

A infraestrutura em `infra/aws` cria um bucket S3 privado e publica frontend e API na mesma origem HTTPS. O modo padrão usa CloudFront e AWS WAF, com rate limit global para autenticação. Se uma conta nova ainda não estiver liberada para CloudFront, o modo `lambda_url` usa uma Lambda Function URL como endpoint HTTPS temporário. Nos dois modos, o proxy entrega o Angular e encaminha `/api`, `/swagger-ui`, `/v3/api-docs` e `/actuator/health` ao ALB com um token privado.

Depois de implantar o backend, use os outputs `api_origin_domain` e `origin_token`:

```powershell
cd infra/aws
Copy-Item terraform.tfvars.example terraform.tfvars
.\deploy.ps1 -ApiOriginDomain "DOMINIO_DO_ALB" -OriginToken "TOKEN_DO_ORIGIN"
```

Para uma conta que retorne a mensagem de verificação obrigatória ao criar CloudFront, acrescente `-HostingMode lambda_url`. O script instala as dependências, testa, gera o build, aplica o Terraform e sincroniza o S3. No modo CloudFront, também invalida o cache. O output `application_url` é o endereço público da aplicação. Depois, atualize `public_base_url` no Terraform do backend com essa URL para que os callbacks do contrato mobile também usem HTTPS.

Para remover os recursos:

```powershell
terraform destroy
```

O workflow `.github/workflows/deploy-aws.yml` publica um novo build mediante acionamento manual. Configure `AWS_ROLE_ARN`, `AWS_REGION`, `WEB_BUCKET` e `CLOUDFRONT_DISTRIBUTION` como variáveis do repositório. A autenticação com a AWS usa OIDC.

## Licença

Consulte [LICENSE](LICENSE).
