package br.com.sicredi.desafiovotacao.adapter.in.web.response;

import br.com.sicredi.desafiovotacao.domain.Resultado;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Apuração dos votos confirmados no snapshot da consulta.")
public record ResultadoResponse(
        @Schema(description = "Identificador da pauta.", example = "ef04ac30-7d84-4a18-9d50-2e523cf00c1f") UUID pautaId,
        @Schema(description = "Situação da sessão.", example = "ABERTA") Resultado.Situacao situacao,
        @Schema(description = "Votos SIM.", example = "3", minimum = "0") long sim,
        @Schema(description = "Votos NAO.", example = "1", minimum = "0") long nao,
        @Schema(description = "Total de votos.", example = "4", minimum = "0") long total,
        @Schema(description = "PENDENTE até o encerramento; depois, decisão conforme os totais.",
                example = "PENDENTE") Resultado.Decisao decisao,
        @Schema(description = "Instante do snapshot em UTC.") Instant apuradoEm,
        @Schema(description = "Abertura da sessão; nulo antes de abrir.") Instant abertaEm,
        @Schema(description = "Encerramento da sessão; nulo antes de abrir.") Instant encerraEm) {
    public static ResultadoResponse from(Resultado resultado) {
        return new ResultadoResponse(resultado.pautaId(), resultado.situacao(), resultado.sim(),
                resultado.nao(), resultado.total(), resultado.decisao(), resultado.apuradoEm(), resultado.abertaEm(), resultado.encerraEm());
    }
}