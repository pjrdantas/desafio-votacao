package br.com.sicredi.desafiovotacao.adapter.in.web.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Contrato de erro comum às APIs REST e mobile.")
public record ApiErrorResponse(
        @Schema(description = "Instante do erro em UTC.") Instant timestamp,
        @Schema(description = "Status HTTP.", example = "400") int status,
        @Schema(description = "Código estável do erro.", example = "VALIDATION_ERROR") String error,
        @Schema(description = "Mensagem para o cliente.", example = "Dados de entrada inválidos.") String message,
        @Schema(description = "Caminho solicitado, sem query string.", example = "/api/v1/pautas") String path,
        @Schema(description = "Erros por campo; lista vazia quando não se aplica.") List<ApiFieldError> fields,
        @Schema(description = "Identificador também retornado em X-Correlation-ID.",
                example = "d324ecdd-b0f6-4bc8-a144-c41a07c01f89") String correlationId) {

    public ApiErrorResponse {
        fields = List.copyOf(fields);
    }
}