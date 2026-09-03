package br.com.sicredi.desafiovotacao.adapter.in.web.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Erro de validação de um campo, sem o valor rejeitado.")
public record ApiFieldError(
        @Schema(description = "Nome do campo.", example = "titulo") String field,
        @Schema(description = "Orientação para corrigir o campo.", example = "O título é obrigatório.") String message) {
}