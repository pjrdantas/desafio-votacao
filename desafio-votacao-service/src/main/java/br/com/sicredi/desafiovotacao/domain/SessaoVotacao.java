package br.com.sicredi.desafiovotacao.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.sicredi.desafiovotacao.domain.exception.DadosInvalidosException;

public record SessaoVotacao(UUID pautaId, Instant abertaEm, Instant encerraEm) {
    public SessaoVotacao {
        Objects.requireNonNull(pautaId);
        Objects.requireNonNull(abertaEm);
        Objects.requireNonNull(encerraEm);
        if (!encerraEm.isAfter(abertaEm)) {
            throw new DadosInvalidosException("Período da sessão inválido.");
        }
    }

    public static int validarDuracao(Integer duracaoMinutos) {
        int minutos = duracaoMinutos == null ? 1 : duracaoMinutos;
        if (minutos <= 0) {
            throw new DadosInvalidosException("Duração deve ser um inteiro positivo em minutos.");
        }
        return minutos;
    }

    public boolean abertaEm(Instant instante) {
        return !instante.isBefore(abertaEm) && instante.isBefore(encerraEm);
    }
}