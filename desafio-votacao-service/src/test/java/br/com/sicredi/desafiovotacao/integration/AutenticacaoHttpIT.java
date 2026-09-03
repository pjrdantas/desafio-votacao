package br.com.sicredi.desafiovotacao.integration;

import br.com.sicredi.desafiovotacao.application.port.out.ConsultaCpfClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"app.security.key-store-path=target/test-jwt/auth.jwk", "app.security.auth-requests-per-minute=10000"})
class AutenticacaoHttpIT {
    static final PostgreSQLContainer DB = new PostgreSQLContainer("postgres:17-alpine");
    static { DB.start(); }
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
    }
    @AfterAll static void stop() { DB.stop(); }
    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired JwtEncoder encoder;
    @MockitoBean ConsultaCpfClient elegibilidade;
    static final AtomicLong CPF = new AtomicLong(200000000);
    static final String SENHA = "Senha-teste-2026!";
    final JsonMapper json = JsonMapper.builder().build();
    final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
    final HttpClient semCookies = HttpClient.newHttpClient();
    @BeforeEach void elegivel() { when(elegibilidade.consultar(anyString())).thenReturn(ConsultaCpfClient.Status.ABLE_TO_VOTE); }

    @Test void cadastroValidaCpfNormalizaDuplicidadeEArmazenaSomenteHash() throws Exception {
        var invalido = auth("cadastro", Map.of("nome", "Teste", "cpf", "11111111111", "senha", SENHA));
        erro(invalido, 400, "CPF_INVALIDO");
        String cpf = cpf();
        String formatado = cpf.substring(0,3)+"."+cpf.substring(3,6)+"."+cpf.substring(6,9)+"-"+cpf.substring(9);
        var criado = auth("cadastro", Map.of("nome", "Avaliador", "cpf", formatado, "senha", SENHA));
        assertThat(criado.statusCode()).isEqualTo(201);
        assertThat(criado.body()).doesNotContain(cpf, SENHA, "senhaHash");
        String hash = jdbc.sql("select senha_hash from usuario where cpf=:cpf").param("cpf", cpf).query(String.class).single();
        assertThat(hash).startsWith("$2").isNotEqualTo(SENHA);
        erro(auth("cadastro", Map.of("nome", "Outro", "cpf", cpf, "senha", SENHA)),409,"CPF_JA_CADASTRADO");
        erro(auth("cadastro", Map.of("nome", "Teste", "cpf", cpf(), "senha", "curta")),400,"VALIDATION_ERROR");
    }

    @Test void loginCookieEJwtNaoExpoemCpfESemBearerNaoPermitemApi() throws Exception {
        String cpf = cadastrar();
        var login = auth("login", Map.of("cpf", cpf, "senha", SENHA));
        assertThat(login.statusCode()).isEqualTo(200);
        String cookie = login.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("VOTACAO_REFRESH=")).findFirst().orElseThrow();
        assertThat(cookie).contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");
        assertThat(login.headers().firstValue("Cache-Control")).hasValue("no-store");
        String token = node(login).get("accessToken").asText();
        String claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(claims).doesNotContain(cpf, SENHA, "refresh");
        assertThat(get("/api/v1/auth/me", token).statusCode()).isEqualTo(200);
        erro(get("/api/v1/pautas", null),401,"NAO_AUTENTICADO");
        assertThat(get("/api/v1/pautas", token).statusCode()).isEqualTo(200);
        erro(auth("login", Map.of("cpf",cpf,"senha","senha-errada")),401,"CREDENCIAIS_INVALIDAS");
    }

    @Test void csrfObrigatorioEmTodasAsMutacoesDeAutenticacao() throws Exception {
        for (String endpoint : List.of("cadastro","login","renovar","logout")) {
            var req = HttpRequest.newBuilder(uri("/api/v1/auth/"+endpoint)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            erro(semCookies.send(req,HttpResponse.BodyHandlers.ofString()),403,"CSRF_INVALIDO");
        }
        var req = HttpRequest.newBuilder(uri("/api/v1/auth/login")).header("Content-Type","application/json")
            .header("X-XSRF-TOKEN","forjado").header("Cookie","XSRF-TOKEN=diferente")
            .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        erro(semCookies.send(req,HttpResponse.BodyHandlers.ofString()),403,"CSRF_INVALIDO");
    }

    @Test void rotacaoRejeitaReusoERevogaJwtDaSessao() throws Exception {
        String token = login(cadastrar());
        String antigo = cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals("VOTACAO_REFRESH"))
            .findFirst().orElseThrow().getValue();
        var renovado = auth("renovar", Map.of());
        assertThat(renovado.statusCode()).isEqualTo(200);
        String novo = node(renovado).get("accessToken").asText();
        assertThat(novo).isNotEqualTo(token);
        assertThat(get("/api/v1/pautas",novo).statusCode()).isEqualTo(200);
        String csrf = node(get("/api/v1/auth/csrf",null)).get("token").asText();
        var replay = HttpRequest.newBuilder(uri("/api/v1/auth/renovar")).header("X-XSRF-TOKEN",csrf)
            .header("Cookie","XSRF-TOKEN="+csrf+"; VOTACAO_REFRESH="+antigo)
            .POST(HttpRequest.BodyPublishers.noBody()).build();
        erro(semCookies.send(replay,HttpResponse.BodyHandlers.ofString()),401,"NAO_AUTENTICADO");
        erro(get("/api/v1/pautas",novo),401,"NAO_AUTENTICADO");
    }

    @Test void logoutRevogaBearerEExpiracaoDaSessaoImpedeRenovacao() throws Exception {
        String token = login(cadastrar());
        assertThat(auth("logout",Map.of()).statusCode()).isEqualTo(204);
        erro(get("/api/v1/pautas",token),401,"NAO_AUTENTICADO");
        erro(auth("renovar",Map.of()),401,"NAO_AUTENTICADO");
        String outro = login(cadastrar());
        String sid = claims(outro).get("sid").asText();
        jdbc.sql("update sessao_acesso set expira_em=now()-interval '1 second' where id=:id").param("id",UUID.fromString(sid)).update();
        erro(get("/api/v1/pautas",outro),401,"NAO_AUTENTICADO");
        erro(auth("renovar",Map.of()),401,"NAO_AUTENTICADO");
    }

    @Test void jwtForjadoExpiradoSemEscopoOuComIssuerAudienceErradosNaoAutoriza() throws Exception {
        String token = login(cadastrar());
        JsonNode original = claims(token);
        String sid = original.get("sid").asText(), sub = original.get("sub").asText();
        String[] partes = token.split("\\.");
        String adulterado = partes[0]+"."+partes[1]+"."+ (partes[2].charAt(0)=='A'?"B":"A")+partes[2].substring(1);
        erro(get("/api/v1/pautas", adulterado),401,"NAO_AUTENTICADO");
        for (String caso : List.of("expirado","issuer","audience","sem-expiracao","sessao")) {
            var c = JwtClaimsSet.builder().subject(sub).claim("sid",caso.equals("sessao")?UUID.randomUUID().toString():sid)
                .issuedAt(Instant.now().minusSeconds(120))
                .issuer(caso.equals("issuer")?"outro":"urn:desafio-votacao-service")
                .audience(List.of(caso.equals("audience")?"outra":"votacao-api")).claim("scope","votacao");
            if (!caso.equals("sem-expiracao")) c.expiresAt(Instant.now().plusSeconds(caso.equals("expirado")?-60:600));
            String invalido = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),c.build())).getTokenValue();
            erro(get("/api/v1/pautas",invalido),401,"NAO_AUTENTICADO");
        }
        var c = JwtClaimsSet.builder().subject(sub).claim("sid",sid).issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(600)).issuer("urn:desafio-votacao-service").audience(List.of("votacao-api")).build();
        String semEscopo = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(),c)).getTokenValue();
        assertThat(get("/api/v1/pautas",semEscopo).statusCode()).isEqualTo(403);
    }

    @Test void votoUsaIdentidadeDoJwtImpedeTrocaDeAssociadoEAplicaElegibilidade() throws Exception {
        String token = login(cadastrar());
        var pauta = post("/api/v1/pautas",Map.of("titulo","Teste autenticado"),token);
        assertThat(pauta.statusCode()).isEqualTo(201);
        String id = node(pauta).get("id").asText();
        assertThat(post("/api/v1/pautas/"+id+"/sessao",Map.of("duracaoMinutos",10),token).statusCode()).isEqualTo(201);
        when(elegibilidade.consultar(anyString())).thenReturn(ConsultaCpfClient.Status.UNABLE_TO_VOTE);
        erro(post("/api/v1/pautas/"+id+"/votos",Map.of("escolha","SIM"),token),404,"UNABLE_TO_VOTE");
        when(elegibilidade.consultar(anyString())).thenReturn(ConsultaCpfClient.Status.ABLE_TO_VOTE);
        assertThat(post("/api/v1/pautas/"+id+"/votos",Map.of("escolha","SIM","associadoId","forjado"),token).statusCode()).isEqualTo(201);
        erro(post("/api/v1/pautas/"+id+"/votos",Map.of("escolha","NAO","associadoId","outra-pessoa"),token),409,"VOTO_DUPLICADO");
        String salvo = jdbc.sql("select associado_id from voto where pauta_id=:id").param("id",UUID.fromString(id)).query(String.class).single();
        assertThat(salvo).isEqualTo(claims(token).get("sub").asText());
        assertThat(node(get("/api/v1/pautas/"+id+"/resultado",token)).get("total").asInt()).isEqualTo(1);
    }

    @Test void cincoSenhasErradasBloqueiamTemporariamente() throws Exception {
        String cpf = cadastrar();
        for (int i=0;i<5;i++) erro(auth("login",Map.of("cpf",cpf,"senha","incorreta")),401,"CREDENCIAIS_INVALIDAS");
        erro(auth("login",Map.of("cpf",cpf,"senha",SENHA)),401,"CREDENCIAIS_INVALIDAS");
        jdbc.sql("update usuario set bloqueado_ate=now()-interval '1 second' where cpf=:cpf").param("cpf",cpf).update();
        assertThat(auth("login",Map.of("cpf",cpf,"senha",SENHA)).statusCode()).isEqualTo(200);
    }

    String cadastrar() throws Exception {
        String cpf = cpf();
        assertThat(auth("cadastro",Map.of("nome","Avaliador de teste","cpf",cpf,"senha",SENHA)).statusCode()).isEqualTo(201);
        return cpf;
    }
    String login(String cpf) throws Exception {
        var r=auth("login",Map.of("cpf",cpf,"senha",SENHA)); assertThat(r.statusCode()).isEqualTo(200);
        return node(r).get("accessToken").asText();
    }
    HttpResponse<String> auth(String endpoint,Object body) throws Exception {
        JsonNode csrf = node(get("/api/v1/auth/csrf",null));
        return client.send(HttpRequest.newBuilder(uri("/api/v1/auth/"+endpoint))
            .header("Content-Type","application/json").header(csrf.get("headerName").asText(),csrf.get("token").asText())
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> get(String path,String token) throws Exception {
        var req = HttpRequest.newBuilder(uri(path)).GET(); if(token!=null)req.header("Authorization","Bearer "+token);
        return client.send(req.build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> post(String path,Object body,String token) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).header("Authorization","Bearer "+token)
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    URI uri(String path) { return URI.create("http://localhost:"+port+path); }
    JsonNode node(HttpResponse<String> r) { return json.readTree(r.body()); }
    JsonNode claims(String token) { return json.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1])); }
    void erro(HttpResponse<String> r,int status,String codigo) {
        assertThat(r.statusCode()).as(r.body()).isEqualTo(status);
        assertThat(node(r).get("error").asText()).isEqualTo(codigo);
        assertThat(node(r).get("correlationId").asText()).isEqualTo(r.headers().firstValue("X-Correlation-ID").orElseThrow());
    }
    static String cpf() {
        String valor=Long.toString(CPF.incrementAndGet());
        for(int etapa=0;etapa<2;etapa++) {
            int soma=0; for(int i=0;i<valor.length();i++) soma+=(valor.charAt(i)-'0')*(valor.length()+1-i);
            int digito=11-soma%11; valor+=digito>=10?0:digito;
        }
        return valor;
    }
}
