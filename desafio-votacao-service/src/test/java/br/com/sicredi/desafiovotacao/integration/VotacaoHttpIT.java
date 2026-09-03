package br.com.sicredi.desafiovotacao.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.mobile-base-url=https://mobile.exemplo.test", "app.cpf-fake.modo=apto", "app.security.key-store-path=target/test-jwt/votacao.jwk", "app.security.auth-requests-per-minute=10000"})
@org.springframework.context.annotation.Import(VotacaoHttpIT.FalhasController.class)
class VotacaoHttpIT {
    private static final PostgreSQLContainer POSTGRES;
    static {
        if (System.getProperty("it.jdbc.url") == null) {
            POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES == null
                ? System.getProperty("it.jdbc.url") : POSTGRES.getJdbcUrl());
        registry.add("spring.datasource.username", () -> POSTGRES == null
                ? System.getProperty("it.jdbc.username", "votacao") : POSTGRES.getUsername());
        registry.add("spring.datasource.password", () -> POSTGRES == null
                ? System.getProperty("it.jdbc.password", "") : POSTGRES.getPassword());
    }

    @AfterAll
    static void pararContainer() {
        if (POSTGRES != null) POSTGRES.stop();
    }

    @LocalServerPort
    int port;
    @Autowired
    JdbcClient jdbc;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    @Autowired br.com.sicredi.desafiovotacao.application.port.out.TokenAcessoEncoder tokenEncoder;
    private final Map<String, String> tokens = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong CPFS = new java.util.concurrent.atomic.AtomicLong(100000000);
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Test
    void healthProbesEMetricasInternasDisponiveisSemExporEndpointDeMetricas() throws Exception {
        assertThat(get("/actuator/health/liveness").json().get("status").asText()).isEqualTo("UP");
        assertThat(get("/actuator/health/readiness").json().get("status").asText()).isEqualTo("UP");
        assertThat(get("/actuator/metrics").status()).isEqualTo(404);
        assertThat(meterRegistry.find("jvm.memory.used").meters()).isNotEmpty();
        assertThat(meterRegistry.find("hikaricp.connections.max").meters()).isNotEmpty();
    }

    @Test
    void fluxoHttpRegistraVotoAceitoERejeitadoSeparadamente() throws Exception {
        var aceitos = meterRegistry.timer("votacao.operacoes", "operacao", "votar", "resultado", "sucesso", "motivo", "NENHUM");
        var duplicados = meterRegistry.timer("votacao.operacoes", "operacao", "votar", "resultado", "rejeitada", "motivo", "VOTO_DUPLICADO");
        long aceitosAntes = aceitos.count();
        long duplicadosAntes = duplicados.count();
        String id = criarPauta();
        assertThat(post("/api/v1/pautas/" + id + "/sessao", "{}").status()).isEqualTo(201);
        assertThat(votar(id, "metrica", "SIM").status()).isEqualTo(201);
        assertThat(votar(id, "metrica", "NAO").status()).isEqualTo(409);
        assertThat(aceitos.count() - aceitosAntes).isEqualTo(1);
        assertThat(duplicados.count() - duplicadosAntes).isEqualTo(1);
    }

    @Test
    void fluxoCompletoComDuracaoPadraoVotosParcialEFinal() throws Exception {
        String id = criarPauta();
        assertThat(get("/api/v1/pautas/" + id + "/resultado").json().get("situacao").asText()).isEqualTo("NAO_ABERTA");
        Resposta abertura = post("/api/v1/pautas/" + id + "/sessao", "{}");
        assertThat(abertura.status()).isEqualTo(201);
        assertThat(Duration.between(Instant.parse(abertura.json().get("abertaEm").asText()),
                Instant.parse(abertura.json().get("encerraEm").asText()))).isEqualTo(Duration.ofMinutes(1));
        assertThat(votar(id, "1", "SIM").status()).isEqualTo(201);
        assertThat(votar(id, "2", "NAO").status()).isEqualTo(201);
        Resposta parcial = get("/api/v1/pautas/" + id + "/resultado");
        assertThat(parcial.json().get("total").asLong()).isEqualTo(2);
        assertThat(parcial.json().get("decisao").asText()).isEqualTo("PENDENTE");
        encerrar(id);
        Resposta encerrado = get("/api/v1/pautas/" + id + "/resultado");
        assertThat(encerrado.json().get("situacao").asText()).isEqualTo("ENCERRADA");
        assertThat(encerrado.json().get("decisao").asText()).isEqualTo("EMPATE");
        assertThat(votar(id, "3", "SIM").status()).isEqualTo(409);
    }

    @Test
    void sessaoNaoAbertaEDuplicadaSaoConflitos() throws Exception {
        String id = criarPauta();
        assertThat(votar(id, "1", "SIM").json().get("error").asText()).isEqualTo("SESSAO_NAO_ABERTA");
        assertThat(post("/api/v1/pautas/" + id + "/sessao", "{\"duracaoMinutos\":5}").status()).isEqualTo(201);
        assertThat(post("/api/v1/pautas/" + id + "/sessao", "{}").status()).isEqualTo(409);
        encerrar(id);
        assertThat(post("/api/v1/pautas/" + id + "/sessao", "{}").status()).isEqualTo(409);
    }

    @Test
    void concorrenciaAceitaExatamenteUmVotoDoMesmoAssociado() throws Exception {
        String id = criarPauta();
        post("/api/v1/pautas/" + id + "/sessao", "{}");
        List<Integer> statuses = simultaneos(12, index -> votar(id, "mesmo-associado", "SIM").status());
        assertThat(Collections.frequency(statuses, 201)).isEqualTo(1);
        assertThat(Collections.frequency(statuses, 409)).isEqualTo(11);
        assertThat(get("/api/v1/pautas/" + id + "/resultado").json().get("total").asLong()).isEqualTo(1);
    }

    @Test
    void concorrenciaAceitaAssociadosDiferentesSemPerderVotos() throws Exception {
        String id = criarPauta();
        post("/api/v1/pautas/" + id + "/sessao", "{}");
        assertThat(simultaneos(20, index -> votar(id, "associado-" + index, index % 2 == 0 ? "SIM" : "NAO").status()))
                .containsOnly(201);
        Resposta resultado = get("/api/v1/pautas/" + id + "/resultado");
        assertThat(resultado.json().get("total").asLong()).isEqualTo(20);
        assertThat(resultado.json().get("sim").asLong()).isEqualTo(10);
        assertThat(resultado.json().get("nao").asLong()).isEqualTo(10);
    }

    @Test
    void concorrenciaAbreExatamenteUmaSessao() throws Exception {
        String id = criarPauta();
        List<Integer> statuses = simultaneos(8, index -> post("/api/v1/pautas/" + id + "/sessao", "{}").status());
        assertThat(Collections.frequency(statuses, 201)).isEqualTo(1);
        assertThat(Collections.frequency(statuses, 409)).isEqualTo(7);
    }

    @Test
    void associadoPodeVotarEmPautasDiferentes() throws Exception {
        String primeira = criarPauta();
        String segunda = criarPauta();
        post("/api/v1/pautas/" + primeira + "/sessao", "{}");
        post("/api/v1/pautas/" + segunda + "/sessao", "{}");
        assertThat(votar(primeira, "1", "SIM").status()).isEqualTo(201);
        assertThat(votar(segunda, "1", "NAO").status()).isEqualTo(201);
        assertThat(votar(primeira, " 1 ", "NAO").status()).isEqualTo(409);
    }

    @Test
    void errosDeEntradaEPautaAusentePossuemStatusAdequados() throws Exception {
        assertThat(post("/api/v1/pautas", "{\"titulo\":\" \"}").status()).isEqualTo(400);
        assertThat(post("/api/v1/pautas", "{").status()).isEqualTo(400);
        assertThat(get("/api/v1/pautas/nao-e-uuid").status()).isEqualTo(400);
        assertThat(get("/api/v1/pautas/" + UUID.randomUUID()).status()).isEqualTo(404);
        assertThat(get("/api/v1/pautas?pagina=-1").status()).isEqualTo(400);
        assertThat(get("/api/v1/pautas?tamanho=101").status()).isEqualTo(400);
        String id = criarPauta();
        assertThat(post("/api/v1/pautas/" + id + "/sessao", "{\"duracaoMinutos\":0}").status()).isEqualTo(400);
        assertThat(post("/api/v1/pautas/" + id + "/sessao", "{\"duracaoMinutos\":1.5}").status()).isEqualTo(400);
        assertThat(votar(id, "1", "TALVEZ").status()).isEqualTo(400);
    }

    @Test
    void contratoMobilePermiteNavegarCriarAbrirVotarEConsultarResultado() throws Exception {
        JsonNode inicio = get("/api/v1/mobile").json();
        assertThat(inicio.get("tipo").asText()).isEqualTo("FORMULARIO");
        JsonNode formulario = executar(inicio.get("botoes").get(0), Map.of()).json();
        assertThat(formulario.get("itens").get(0).get("id").asText()).isEqualTo("titulo");
        JsonNode detalhe = executar(formulario.get("botoes").get(0), Map.of("titulo", "Pauta mobile")).json();
        assertThat(detalhe.get("tipo").asText()).isEqualTo("SELECAO");
        JsonNode abrir = executar(detalhe.get("opcoes").get(0), Map.of()).json();
        JsonNode aberta = executar(abrir.get("botoes").get(0), Map.of("duracaoMinutos", 5)).json();
        JsonNode votar = executar(aberta.get("opcoes").get(0), Map.of()).json();
        assertThat(votar.get("botoes").size()).isEqualTo(2);
        JsonNode resultado = executar(votar.get("botoes").get(0), Map.of()).json();
        assertThat(resultado.get("titulo").asText()).isEqualTo("Resultado da votação");
        assertThat(resultado.get("itens").get(3).get("valor").asLong()).isEqualTo(1);
        JsonNode lista = executar(resultado.get("botoes").get(1), Map.of()).json();
        assertThat(lista.get("tipo").asText()).isEqualTo("SELECAO");
    }

    @Test
    void openApiEHealthDisponiveis() throws Exception {
        Resposta schema = get("/v3/api-docs");
        assertThat(schema.status()).isEqualTo(200);
        assertThat(schema.json().get("paths").has("/api/v1/pautas/{id}/votos")).isTrue();
        assertThat(get("/actuator/health").json().get("status").asText()).isEqualTo("UP");
    }


    @Test
    void validacaoInformaCamposSemExporValoresRejeitados() throws Exception {
        Resposta response = post("/api/v1/pautas", "{\"titulo\":\" \"}");
        validarErro(response, 400, "VALIDATION_ERROR", "/api/v1/pautas");
        assertThat(response.json().get("fields").get(0).get("field").asText()).isEqualTo("titulo");
        assertThat(response.json().get("fields").get(0).get("message").asText()).isEqualTo("O título é obrigatório.");
        assertThat(response.json().toString()).doesNotContain("rejectedValue", "stackTrace", "exception");
        Resposta mobile = post("/api/v1/mobile/pautas", "{\"titulo\":\" \"}");
        validarErro(mobile, 400, "VALIDATION_ERROR", "/api/v1/mobile/pautas");
    }

    @Test
    void formatoInvalidoEParametroInvalidoSeguemContratoComum() throws Exception {
        validarErro(post("/api/v1/pautas", "{"), 400, "INVALID_REQUEST_BODY", "/api/v1/pautas");
        Resposta uuid = get("/api/v1/pautas/nao-e-uuid");
        validarErro(uuid, 400, "VALIDATION_ERROR", "/api/v1/pautas/nao-e-uuid");
        assertThat(uuid.json().get("fields").get(0).get("field").asText()).isEqualTo("id");
        Resposta pagina = get("/api/v1/pautas?pagina=abc");
        validarErro(pagina, 400, "VALIDATION_ERROR", "/api/v1/pautas");
        assertThat(pagina.json().get("fields").get(0).get("field").asText()).isEqualTo("pagina");
    }

    @Test
    void pautaAusenteConflitosERotaInexistenteSeguemContratoComum() throws Exception {
        String missingPath = "/api/v1/pautas/" + UUID.randomUUID();
        validarErro(get(missingPath), 404, "PAUTA_NAO_ENCONTRADA", missingPath);
        validarErro(get("/api/v1/rota-inexistente"), 404, "RESOURCE_NOT_FOUND", "/api/v1/rota-inexistente");
        String id = criarPauta();
        String votoPath = "/api/v1/pautas/" + id + "/votos";
        validarErro(votar(id, "1", "SIM"), 409, "SESSAO_NAO_ABERTA", votoPath);
        post("/api/v1/pautas/" + id + "/sessao", "{}");
        validarErro(post("/api/v1/pautas/" + id + "/sessao", "{}"),
                409, "SESSAO_JA_EXISTE", "/api/v1/pautas/" + id + "/sessao");
        votar(id, "1", "SIM");
        validarErro(votar(id, "1", "NAO"), 409, "VOTO_DUPLICADO", votoPath);
        encerrar(id);
        validarErro(votar(id, "2", "SIM"), 409, "SESSAO_ENCERRADA", votoPath);
    }

    @Test
    void metodosETiposDeMidiaInvalidosMantemStatusEHeaders() throws Exception {
        Resposta metodo = enviar(HttpRequest.newBuilder(uri("/api/v1/pautas"))
                .method("DELETE", HttpRequest.BodyPublishers.noBody()).build());
        validarErro(metodo, 405, "METHOD_NOT_ALLOWED", "/api/v1/pautas");
        assertThat(metodo.headers().firstValue("Allow").orElseThrow()).contains("GET", "POST");
        Resposta formato = enviar(HttpRequest.newBuilder(uri("/api/v1/pautas"))
                .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString("texto")).build());
        validarErro(formato, 415, "UNSUPPORTED_MEDIA_TYPE", "/api/v1/pautas");
        Resposta accept = enviar(HttpRequest.newBuilder(uri("/api/v1/pautas"))
                .header("Accept", "application/xml").GET().build());
        validarErro(accept, 406, "NOT_ACCEPTABLE", "/api/v1/pautas");
    }

    @Test
    void errosTecnicosNaoExpoemDetalhesInternos() throws Exception {
        Resposta inesperado = get("/_test/errors/unexpected");
        validarErro(inesperado, 500, "INTERNAL_ERROR", "/_test/errors/unexpected");
        assertThat(inesperado.json().toString()).doesNotContain("sentinela-interna", "IllegalStateException");
        Resposta unavailable = get("/_test/errors/unavailable");
        validarErro(unavailable, 503, "SERVICE_UNAVAILABLE", "/_test/errors/unavailable");
        assertThat(unavailable.json().toString()).doesNotContain("jdbc:", "sentinela-interna");
        Resposta integrity = get("/_test/errors/integrity");
        validarErro(integrity, 409, "DATA_INTEGRITY_CONFLICT", "/_test/errors/integrity");
        assertThat(integrity.json().toString()).doesNotContain("SQL", "sentinela-interna");
    }

    @Test
    void correlacaoRecebidaOuGeradaEhDevolvidaEmSucessoEErro() throws Exception {
        String header = "X-Correlation-ID";
        String supplied = "teste-correlacao-123";
        Resposta sucesso = enviar(HttpRequest.newBuilder(uri("/api/v1/pautas")).header(header, supplied).GET().build());
        assertThat(sucesso.headers().firstValue(header)).contains(supplied);
        String missingPath = "/api/v1/pautas/" + UUID.randomUUID();
        Resposta erro = enviar(HttpRequest.newBuilder(uri(missingPath)).header(header, supplied).GET().build());
        assertThat(erro.json().get("correlationId").asText()).isEqualTo(supplied);
        Resposta invalido = enviar(HttpRequest.newBuilder(uri(missingPath)).header(header, "invalido com espacos").GET().build());
        assertThat(invalido.json().get("correlationId").asText()).isNotEqualTo("invalido com espacos");
        assertThatCode(() -> UUID.fromString(invalido.json().get("correlationId").asText())).doesNotThrowAnyException();
        Resposta novo = get(missingPath);
        assertThat(novo.json().get("correlationId").asText()).isNotEqualTo(invalido.json().get("correlationId").asText());
    }

    @Test
    void swaggerDocumentaStatusModelosExemplosGruposEAutenticacao() throws Exception {
        JsonNode api = get("/v3/api-docs").json();
        JsonNode paths = api.get("paths");
        JsonNode criar = paths.get("/api/v1/pautas").get("post");
        assertThat(criar.get("responses").has("201")).isTrue();
        assertThat(criar.get("responses").has("200")).isFalse();
        assertThat(criar.get("requestBody").get("content").get("application/json").get("schema").get("$ref").asText())
                .endsWith("/CriarPautaRequest");
        JsonNode vote = paths.get("/api/v1/pautas/{id}/votos").get("post").get("responses");
        assertThat(vote.has("201")).isTrue();
        assertThat(vote.get("201").has("content")).isFalse();
        assertThat(vote.get("409").get("$ref").asText()).isEqualTo("#/components/responses/Conflict");
        JsonNode schemas = api.get("components").get("schemas");
        assertThat(schemas.get("ApiErrorResponse").get("properties").propertyNames())
                .contains("timestamp", "status", "error", "message", "path", "fields", "correlationId");
        assertThat(schemas.has("ApiFieldError")).isTrue();
        assertThat(schemas.has("PautaResponse")).isTrue();
        assertThat(schemas.has("SessaoResponse")).isTrue();
        assertThat(schemas.has("ResultadoResponse")).isTrue();
        assertThat(schemas.has("CampoMobile")).isTrue();
        assertThat(schemas.has("AcaoMobile")).isTrue();
        JsonNode badRequest = api.get("components").get("responses").get("BadRequest");
        assertThat(badRequest.get("content").get("application/json").get("examples").get("exemplo")
                .get("value").get("fields").size()).isEqualTo(1);
        assertThat(api.path("security").isEmpty()).isTrue();
        assertThat(api.get("components").path("securitySchemes").path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
        for (var path : paths.properties()) {
            for (var entry : path.getValue().properties()) {
                JsonNode operation = entry.getValue();
                if (!operation.has("responses")) continue;
                assertThat(operation.path("summary").asText()).isNotBlank();
                if (!path.getKey().startsWith("/api/v1/auth/") || path.getKey().endsWith("/me")) assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue();
                assertThat(operation.get("responses").has("500")).isTrue();
                long correlationParams = java.util.stream.StreamSupport.stream(operation.path("parameters").spliterator(), false)
                        .filter(p -> p.path("name").asText().equals("X-Correlation-ID")).count();
                assertThat(correlationParams).isEqualTo(1);
                operation.path("tags").forEach(tag -> assertThat(tag.asText()).doesNotEndWith("-controller"));
                if (path.getKey().startsWith("/api/v1/mobile")) {
                    assertThat(operation.get("responses").get("200").get("content").get("application/json")
                            .get("schema").get("$ref").asText()).endsWith("/Tela");
                }
            }
        }
        JsonNode restGroup = get("/v3/api-docs/v1-votacao").json().get("paths");
        assertThat(restGroup.propertyNames()).allMatch(path -> path.startsWith("/api/v1/pautas"));
        JsonNode mobileGroup = get("/v3/api-docs/v1-mobile").json().get("paths");
        assertThat(mobileGroup.propertyNames()).allMatch(path -> path.startsWith("/api/v1/mobile"));
        HttpResponse<String> swagger = client.send(HttpRequest.newBuilder(uri("/swagger-ui/index.html")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(swagger.statusCode()).isEqualTo(200);
        assertThat(swagger.body()).contains("Swagger UI");
    }

    private void validarErro(Resposta response, int status, String error, String path) {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        assertThat(response.json().get("status").asInt()).isEqualTo(status);
        assertThat(response.json().get("error").asText()).isEqualTo(error);
        assertThat(response.json().get("path").asText()).isEqualTo(path);
        assertThat(response.json().get("message").asText()).isNotBlank();
        assertThat(response.json().get("fields").isArray()).isTrue();
        assertThatCode(() -> Instant.parse(response.json().get("timestamp").asText())).doesNotThrowAnyException();
        assertThat(response.json().get("correlationId").asText())
                .isEqualTo(response.headers().firstValue("X-Correlation-ID").orElseThrow());
    }

    @org.springframework.web.bind.annotation.RestController
    @org.springframework.web.bind.annotation.RequestMapping("/_test/errors")
    static class FalhasController {
        @org.springframework.web.bind.annotation.GetMapping("/unexpected")
        void unexpected() { throw new IllegalStateException("sentinela-interna"); }

        @org.springframework.web.bind.annotation.GetMapping("/unavailable")
        void unavailable() {
            throw new org.springframework.dao.DataAccessResourceFailureException("jdbc:sentinela-interna");
        }

        @org.springframework.web.bind.annotation.GetMapping("/integrity")
        void integrity() {
            throw new org.springframework.dao.DataIntegrityViolationException("SQL sentinela-interna");
        }
    }

    private String criarPauta() throws Exception {
        Resposta response = post("/api/v1/pautas", "{\"titulo\":\"Pauta de teste\",\"descricao\":\"Integração\"}");
        assertThat(response.status()).isEqualTo(201);
        return response.json().get("id").asText();
    }

    private void encerrar(String id) {
        jdbc.sql("""
                UPDATE sessao_votacao SET aberta_em = clock_timestamp() - INTERVAL '2 minutes',
                encerra_em = clock_timestamp() - INTERVAL '1 minute' WHERE pauta_id = :id
                """).param("id", UUID.fromString(id)).update();
    }

    private Resposta votar(String id, String associado, String escolha) throws Exception {
        return enviar(HttpRequest.newBuilder(uri("/api/v1/pautas/" + id + "/votos")).header("Authorization", "Bearer " + token(associado.strip()))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                json.writeValueAsString(Map.of("escolha", escolha)))).build());
    }

    private Resposta executar(JsonNode acao, Map<String, Object> campos) throws Exception {
        String url = acao.get("url").asText();
        assertThat(url).startsWith("https://mobile.exemplo.test/api/v1/mobile");
        Map<String, Object> body = new LinkedHashMap<>();
        acao.get("body").properties().forEach(entry -> body.put(entry.getKey(), entry.getValue().asText()));
        body.putAll(campos);
        return post(URI.create(url).getPath(), json.writeValueAsString(body));
    }

    private Resposta get(String path) throws Exception {
        return enviar(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20)).GET().build());
    }

    private Resposta post(String path, String body) throws Exception {
        return enviar(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private String token(String associado) {
        return tokens.computeIfAbsent(associado, key -> {
            UUID usuario = UUID.randomUUID(), sessao = UUID.randomUUID();
            String cpf = String.valueOf(CPFS.incrementAndGet());
            for (int tamanho = 9; tamanho <= 10; tamanho++) {
                int soma = 0;
                for (int i = 0; i < tamanho; i++) soma += (cpf.charAt(i) - '0') * (tamanho + 1 - i);
                int resto = soma % 11;
                cpf += resto < 2 ? 0 : 11 - resto;
            }
            jdbc.sql("INSERT INTO usuario(id,nome,cpf,senha_hash) VALUES (:id,'Fixture HTTP',:cpf,'nao-utilizado-para-login')")
                    .param("id", usuario).param("cpf", cpf).update();
            jdbc.sql("INSERT INTO sessao_acesso(id,usuario_id,refresh_hash,expira_em) VALUES (:id,:usuario,:hash,:fim)")
                    .param("id", sessao).param("usuario", usuario).param("hash", "0".repeat(64))
                    .param("fim", java.sql.Timestamp.from(Instant.now().plusSeconds(3600))).update();
            return tokenEncoder.emitir(usuario, sessao, Instant.now(), Instant.now().plusSeconds(900));
        });
    }
    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private Resposta enviar(HttpRequest request) throws Exception {
        if (request.headers().firstValue("Authorization").isEmpty()) request = HttpRequest.newBuilder(request, (name, value) -> true)
                .header("Authorization", "Bearer " + token("principal")).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return new Resposta(response.statusCode(), response.body().isBlank() ? null : json.readTree(response.body()), response.headers());
    }

    private List<Integer> simultaneos(int quantidade, Operacao operacao) throws Exception {
        CountDownLatch preparados = new CountDownLatch(quantidade);
        CountDownLatch iniciar = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < quantidade; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    preparados.countDown();
                    if (!iniciar.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout na barreira");
                    return operacao.executar(index);
                }));
            }
            assertThat(preparados.await(10, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();
            List<Integer> resultados = new ArrayList<>();
            for (Future<Integer> future : futures) resultados.add(future.get(30, TimeUnit.SECONDS));
            return resultados;
        }
    }

    @FunctionalInterface
    private interface Operacao { int executar(int index) throws Exception; }
    private record Resposta(int status, JsonNode json, HttpHeaders headers) {}
}
