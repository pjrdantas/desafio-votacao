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
| `API_UPSTREAM` | `host.docker.internal:8080` | Destino da API sem protocolo |

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

## Licença

Consulte [LICENSE](LICENSE).
