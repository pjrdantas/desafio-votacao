package br.com.sicredi.desafiovotacao.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.sicredi.desafiovotacao.domain.exception.DadosInvalidosException;

public record Pauta(UUID id, String titulo, String descricao, Instant criadaEm) {
    public Pauta {
        Objects.requireNonNull(id);
        Objects.requireNonNull(criadaEm);
        titulo = titulo == null ? "" : titulo.strip();
        descricao = descricao == null ? "" : descricao.strip();
        if (titulo.isEmpty() || titulo.length() > 200 || descricao.length() > 2000) {
            throw new DadosInvalidosException(
                    "Título obrigatório com até 200 caracteres; descrição com até 2000.");
        }
    }
}