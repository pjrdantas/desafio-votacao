package br.com.sicredi.desafiovotacao.config;

import br.com.sicredi.desafiovotacao.adapter.in.web.CorrelationIdFilter;
import br.com.sicredi.desafiovotacao.adapter.in.web.exception.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.tags.Tag;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI votacaoOpenApi() {
        Components components = new Components().addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"));
        ModelConverters.getInstance().readAll(ApiErrorResponse.class).forEach(components::addSchemas);
        components.addResponses("BadRequest", errorResponse(400, "VALIDATION_ERROR", "Dados de entrada inválidos.",
                List.of(Map.of("field", "titulo", "message", "O título é obrigatório."))));
        components.addResponses("Unauthorized", errorResponse(401, "NAO_AUTENTICADO", "Entre para acessar a aplicação.", List.of()));
        components.addResponses("Forbidden", errorResponse(403, "CSRF_INVALIDO", "Atualize a página e tente novamente.", List.of()));
        components.addResponses("TooManyRequests", errorResponse(429, "LIMITE_TENTATIVAS", "Aguarde antes de tentar novamente.", List.of()));
        components.addResponses("NotFound", errorResponse(404, "PAUTA_NAO_ENCONTRADA", "Pauta não encontrada.", List.of()));
        ApiResponse conflict = errorResponse(409, "VOTO_DUPLICADO", "O associado já votou nesta pauta.", List.of());
        conflict.getContent().get("application/json").addExamples("sessaoEncerrada",
                errorExample(409, "SESSAO_ENCERRADA", "Sessão encerrada.", List.of()));
        conflict.getContent().get("application/json").addExamples("sessaoExistente",
                errorExample(409, "SESSAO_JA_EXISTE", "A pauta já possui uma sessão.", List.of()));
        conflict.setDescription("Conflito com a situação da sessão, voto já registrado ou integridade dos dados.");
        components.addResponses("Conflict", conflict);
        components.addResponses("NotAcceptable", errorResponse(406, "NOT_ACCEPTABLE",
                "Formato de resposta não suportado. Utilize application/json.", List.of()));
        components.addResponses("UnsupportedMediaType", errorResponse(415, "UNSUPPORTED_MEDIA_TYPE",
                "Formato do corpo não suportado. Utilize application/json.", List.of()));
        components.addResponses("InternalError", errorResponse(500, "INTERNAL_ERROR",
                "Erro interno ao processar a requisição.", List.of()));
        components.addResponses("ServiceUnavailable", errorResponse(503, "SERVICE_UNAVAILABLE",
                "Serviço temporariamente indisponível. Tente novamente mais tarde.", List.of()));
        return new OpenAPI().components(components)
                .info(new Info().title("Desafio Votação API").version("v1").description("""
                        API para votação em assembleias e navegação mobile por JSON.
                        Acesse com CPF válido e senha. Utilize o accessToken em Authorize (Bearer JWT). Para POST de autenticação, obtenha o token em GET /api/v1/auth/csrf e informe X-XSRF-TOKEN; mantenha os cookies no mesmo cliente.
                        Erros seguem ApiErrorResponse. X-Correlation-ID é opcional e retornado em todas as respostas.
                        Os callbacks mobile usam POST. O contrato das telas é uma proposta documentada no repositório.
                        """))
                .tags(List.of(new Tag().name("Pautas").description("Cadastro e consulta de pautas."),
                        new Tag().name("Sessões").description("Abertura de sessões com prazo persistido."),
                        new Tag().name("Votos").description("Registro único de voto por associado e pauta."),
                        new Tag().name("Resultados").description("Parciais e resultado após encerramento."),
                        new Tag().name("Mobile").description("Telas FORMULARIO e SELECAO; ações executadas por POST.")));
    }

    @Bean
    OpenApiCustomizer standardizeApiDocumentation() {
        return openApi -> {
            if (openApi.getTags() != null) {
                openApi.getTags().forEach(tag -> tag.setName(normalizeTag(tag.getName())));
            }
            if (openApi.getPaths() == null) return;
            openApi.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                if (operation.getTags() != null) {
                    operation.setTags(operation.getTags().stream().map(this::normalizeTag).distinct().toList());
                }
                if (operation.getParameters() == null || operation.getParameters().stream()
                        .noneMatch(parameter -> CorrelationIdFilter.HEADER.equals(parameter.getName()) && "header".equals(parameter.getIn()))) {
                    operation.addParametersItem(new Parameter().in("header").name(CorrelationIdFilter.HEADER)
                        .description("Correlação opcional; ausente ou inválida, o servidor gera um UUID.")
                        .required(false).schema(new StringSchema().pattern("[A-Za-z0-9._-]{1,100}"))
                        .example("d324ecdd-b0f6-4bc8-a144-c41a07c01f89"));
                }
                boolean publico = path.startsWith("/api/v1/auth/") && !path.endsWith("/me");
                operation.setSecurity(publico ? List.of() : List.of(new SecurityRequirement().addList("bearerAuth")));
                operation.getResponses().addApiResponse("401", reference("Unauthorized"));
                operation.getResponses().addApiResponse("403", reference("Forbidden"));
                if (publico && !path.endsWith("/csrf")) {
                    operation.getResponses().addApiResponse("429", reference("TooManyRequests"));
                    operation.addParametersItem(new Parameter().in("header").name("X-XSRF-TOKEN").required(true)
                        .description("Token retornado por GET /api/v1/auth/csrf. Preserve o cookie recebido nesse GET.")
                        .schema(new StringSchema()));
                }
                operation.getResponses().addApiResponse("400", reference("BadRequest"));
                operation.getResponses().addApiResponse("406", reference("NotAcceptable"));
                operation.getResponses().addApiResponse("500", reference("InternalError"));
                // Telas estáticas não consultam o banco.
                if (!path.equals("/api/v1/mobile") && !path.equals("/api/v1/mobile/pautas/nova")) {
                    operation.getResponses().addApiResponse("503", reference("ServiceUnavailable"));
                }
                if (operation.getRequestBody() != null) {
                    operation.getResponses().addApiResponse("415", reference("UnsupportedMediaType"));
                }
                operation.getResponses().values().stream().filter(response -> response.get$ref() == null)
                        .forEach(response -> response.addHeaderObject(CorrelationIdFilter.HEADER, correlationHeader()));
            }));
        };
    }

    @Bean
    GroupedOpenApi votacaoApi(OpenApiCustomizer standardizeApiDocumentation) {
        return GroupedOpenApi.builder().group("v1-votacao")
                .pathsToMatch("/api/v1/pautas", "/api/v1/pautas/**")
                .addOpenApiCustomizer(standardizeApiDocumentation).build();
    }

    @Bean
    GroupedOpenApi mobileApi(OpenApiCustomizer standardizeApiDocumentation) {
        return GroupedOpenApi.builder().group("v1-mobile")
                .pathsToMatch("/api/v1/mobile", "/api/v1/mobile/**")
                .addOpenApiCustomizer(standardizeApiDocumentation).build();
    }

    @Bean
    GroupedOpenApi autenticacaoApi(OpenApiCustomizer standardizeApiDocumentation) {
        return GroupedOpenApi.builder().group("v1-autenticacao").pathsToMatch("/api/v1/auth/**")
                .addOpenApiCustomizer(standardizeApiDocumentation).build();
    }

    private ApiResponse errorResponse(int status, String error, String message, List<Map<String, String>> fields) {
        return new ApiResponse().description(message)
                .addHeaderObject(CorrelationIdFilter.HEADER, correlationHeader())
                .content(new Content().addMediaType("application/json",
                        new io.swagger.v3.oas.models.media.MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))
                                .addExamples("exemplo", errorExample(status, error, message, fields))));
    }

    private Example errorExample(int status, String error, String message, List<Map<String, String>> fields) {
        return new Example().value(Map.of("timestamp", "2026-09-02T12:00:00Z", "status", status,
                "error", error, "message", message, "path", "/api/v1/pautas",
                "fields", fields, "correlationId", "d324ecdd-b0f6-4bc8-a144-c41a07c01f89"));
    }

    private Header correlationHeader() {
        return new Header().description("Identificador para correlacionar a requisição e os logs.").schema(new StringSchema());
    }

    private ApiResponse reference(String name) {
        return new ApiResponse().$ref("#/components/responses/" + name);
    }

    private String normalizeTag(String tag) {
        if (tag == null) return null;
        String suffix = "-controller";
        return tag.toLowerCase(Locale.ROOT).endsWith(suffix) ? tag.substring(0, tag.length() - suffix.length()) : tag;
    }
}