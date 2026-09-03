package br.com.sicredi.desafiovotacao.domain;

import java.util.Objects;
import java.util.UUID;

import br.com.sicredi.desafiovotacao.domain.exception.DadosInvalidosException;

public record Voto(UUID pautaId, String associadoId, Escolha escolha) {
    public Voto {
        Objects.requireNonNull(pautaId);
        associadoId = associadoId == null ? "" : associadoId.strip();
        if (associadoId.isEmpty() || associadoId.length() > 100 || escolha == null) {
            throw new DadosInvalidosException(
                    "Associado obrigatório com até 100 caracteres e escolha SIM ou NAO.");
        }
    }
}