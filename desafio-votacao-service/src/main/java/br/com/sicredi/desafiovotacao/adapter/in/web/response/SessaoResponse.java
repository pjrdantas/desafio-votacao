package br.com.sicredi.desafiovotacao.adapter.in.web.response;

import br.com.sicredi.desafiovotacao.domain.SessaoVotacao;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Sessão única da pauta. Aceita votos em [abertaEm, encerraEm).")
public record SessaoResponse(
        @Schema(description = "Pauta da sessão.", example = "ef04ac30-7d84-4a18-9d50-2e523cf00c1f") UUID pautaId,
        @Schema(description = "Início do prazo em UTC.") Instant abertaEm,
        @Schema(description = "Fim exclusivo do prazo em UTC.") Instant encerraEm) {
    public static SessaoResponse from(SessaoVotacao sessao) {
        return new SessaoResponse(sessao.pautaId(), sessao.abertaEm(), sessao.encerraEm());
    }
}