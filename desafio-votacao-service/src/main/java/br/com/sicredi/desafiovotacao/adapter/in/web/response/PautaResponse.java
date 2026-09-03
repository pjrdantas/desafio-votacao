package br.com.sicredi.desafiovotacao.adapter.in.web.response;

import br.com.sicredi.desafiovotacao.domain.Pauta;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Pauta cadastrada.")
public record PautaResponse(
        @Schema(description = "Identificador da pauta.", example = "ef04ac30-7d84-4a18-9d50-2e523cf00c1f") UUID id,
        @Schema(description = "Título.", example = "Reforma da sede") String titulo,
        @Schema(description = "Descrição.", example = "Deliberação sobre o orçamento.") String descricao,
        @Schema(description = "Instante da criação em UTC.") Instant criadaEm) {
    public static PautaResponse from(Pauta pauta) {
        return new PautaResponse(pauta.id(), pauta.titulo(), pauta.descricao(), pauta.criadaEm());
    }
}