package br.com.sicredi.desafiovotacao.domain;

import java.time.Instant;
import java.util.UUID;

public record Resultado(UUID pautaId, Situacao situacao, long sim, long nao,
                        long total, Decisao decisao, Instant apuradoEm, Instant abertaEm, Instant encerraEm) {
    public enum Situacao { NAO_ABERTA, ABERTA, ENCERRADA }
    public enum Decisao { PENDENTE, APROVADA, REJEITADA, EMPATE, SEM_VOTOS }

    public static Resultado apurar(UUID pautaId, SessaoVotacao sessao, long sim, long nao, Instant instante) {
        Situacao situacao = sessao == null ? Situacao.NAO_ABERTA
                : sessao.abertaEm(instante) ? Situacao.ABERTA : Situacao.ENCERRADA;
        Decisao decisao = Decisao.PENDENTE;
        if (situacao == Situacao.ENCERRADA) {
            decisao = sim + nao == 0 ? Decisao.SEM_VOTOS
                    : sim == nao ? Decisao.EMPATE : sim > nao ? Decisao.APROVADA : Decisao.REJEITADA;
        }
        return new Resultado(pautaId, situacao, sim, nao, sim + nao, decisao, instante, sessao == null ? null : sessao.abertaEm(), sessao == null ? null : sessao.encerraEm());
    }
}