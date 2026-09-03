# Desafio Votação

Solução para gerenciamento de pautas e sessões de votação.

O repositório contém dois projetos independentes:

- [desafio-votacao-service](desafio-votacao-service/README.md): API Java 21 com Spring Boot, PostgreSQL e arquitetura hexagonal.
- [desafio-votacao-web](desafio-votacao-web/README.md): interface Angular 22 com Angular Material.

O enunciado recebido está preservado em [README-original.md](README-original.md).

## Execução com Docker

Inicie o backend:

```powershell
cd desafio-votacao-service
docker compose up --build -d --wait
```

Em outro terminal, inicie o frontend:

```powershell
cd desafio-votacao-web
docker compose up --build -d
```

Acesse:

- Frontend: http://localhost:4200
- Swagger: http://localhost:8080/swagger-ui.html
- Health check: http://localhost:8080/actuator/health

No primeiro acesso, escolha **Criar conta**. O cadastro exige nome, CPF com dígitos verificadores válidos e senha com pelo menos 10 caracteres.

Para encerrar os containers sem apagar os dados:

```powershell
docker compose down
```

Consulte o README de cada projeto para instalação, configuração, testes e execução sem Docker.

## Estrutura

```text
desafio-votacao/
├── desafio-votacao-service/
├── desafio-votacao-web/
├── README-original.md
├── README.md
└── LICENSE
```

O backend e o frontend podem ser distribuídos como repositórios separados. Cada pasta possui dependências, Dockerfile, Compose, workflow de CI, licença e instruções próprias.
