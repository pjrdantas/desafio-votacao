package br.com.sicredi.desafiovotacao.config;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.dynatrace.DynatraceMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.dynatrace.DynatraceMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

class DynatraceConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MetricsAutoConfiguration.class,
                    DynatraceMetricsExportAutoConfiguration.class));

    @Test
    void execucaoPadraoNaoHabilitaExportacaoNemExigeCredenciais() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(DynatraceMeterRegistry.class);
            assertThat(context.getEnvironment().getProperty("management.dynatrace.metrics.export.enabled"))
                    .isEqualTo("false");
        });
    }

    @Test
    void perfilDynatraceExportaMetricasComTokenEDimensoesDoAmbiente() throws Exception {
        var requests = new CopyOnWriteArrayList<Request>();
        HttpServer collector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        collector.createContext("/api/v2/metrics/ingest", exchange -> {
            try (var input = "gzip".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Content-Encoding"))
                    ? new GZIPInputStream(exchange.getRequestBody()) : exchange.getRequestBody()) {
                String body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                requests.add(new Request(exchange.getRequestHeaders().getFirst("Authorization"), body));
                byte[] response = "{\"linesOk\":1,\"linesInvalid\":0,\"error\":null,\"warnings\":null}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(202, response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });
        collector.start();
        try {
            runner.withPropertyValues("spring.profiles.active=dynatrace", "APP_ENV=teste",
                    "DYNATRACE_METRICS_URI=http://127.0.0.1:" + collector.getAddress().getPort() + "/api/v2/metrics/ingest",
                    "DYNATRACE_API_TOKEN=token-ficticio-de-teste", "DYNATRACE_METRICS_STEP=1s",
                    "management.dynatrace.metrics.export.v2.enrich-with-dynatrace-metadata=false")
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(DynatraceMeterRegistry.class);
                        var registry = context.getBean(DynatraceMeterRegistry.class);
                        registry.timer("votacao.operacoes", "operacao", "votar", "resultado", "sucesso", "motivo", "NENHUM")
                                .record(Duration.ofMillis(125));
                        assertThat(context.getEnvironment().getProperty("logging.structured.format.console"))
                                .isEqualTo("logstash");
                        await().atMost(Duration.ofSeconds(8)).untilAsserted(() ->
                                assertThat(requests).anySatisfy(request -> {
                                    assertThat(request.authorization()).isEqualTo("Api-Token token-ficticio-de-teste");
                                    assertThat(request.body()).contains("desafio.votacao.operacoes", "operacao=votar",
                                            "application=desafio-votacao-service", "environment=teste", "resultado=sucesso", "count=1");
                                }));
                    });
        } finally {
            collector.stop(0);
        }
    }

    @Test
    void logsJsonPreservamCorrelationIdEContextoDynatraceQuandoInjetado() {
        runner.withPropertyValues("spring.profiles.active=dynatrace",
                "management.dynatrace.metrics.export.enabled=false", "APP_ENV=teste")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var logContext = new ch.qos.logback.classic.LoggerContext();
                    logContext.putObject(org.springframework.core.env.Environment.class.getName(), context.getEnvironment());
                    var encoder = new org.springframework.boot.logging.logback.StructuredLogEncoder();
                    encoder.setContext(logContext);
                    encoder.setFormat(context.getEnvironment().getRequiredProperty("logging.structured.format.console"));
                    encoder.start();
                    try {
                        var event = new ch.qos.logback.classic.spi.LoggingEvent(getClass().getName(),
                                logContext.getLogger("test"), ch.qos.logback.classic.Level.INFO, "Voto processado", null, null);
                        event.setMDCPropertyMap(java.util.Map.of("correlationId", "teste-correlacao",
                                "dt.trace_id", "0123456789abcdef0123456789abcdef", "dt.span_id", "0123456789abcdef"));
                        event.addKeyValuePair(new org.slf4j.event.KeyValuePair("operacao", "votar"));
                        var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(encoder.encode(event));
                        assertThat(json.get("correlationId").asText()).isEqualTo("teste-correlacao");
                        assertThat(json.get("dt.trace_id").asText()).isEqualTo("0123456789abcdef0123456789abcdef");
                        assertThat(json.get("dt.span_id").asText()).isEqualTo("0123456789abcdef");
                        assertThat(json.get("operacao").asText()).isEqualTo("votar");
                        assertThat(json.get("application").asText()).isEqualTo("desafio-votacao-service");
                        assertThat(json.get("environment").asText()).isEqualTo("teste");
                        var semAgente = new ch.qos.logback.classic.spi.LoggingEvent(getClass().getName(),
                                logContext.getLogger("test"), ch.qos.logback.classic.Level.INFO, "Sem agente", null, null);
                        semAgente.setMDCPropertyMap(java.util.Map.of("correlationId", "teste-correlacao"));
                        var semTrace = tools.jackson.databind.json.JsonMapper.builder().build().readTree(encoder.encode(semAgente));
                        assertThat(semTrace.has("dt.trace_id")).isFalse();
                    } finally {
                        encoder.stop();
                        logContext.stop();
                    }
                });
    }

    private record Request(String authorization, String body) { }
}
