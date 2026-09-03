package br.com.sicredi.desafiovotacao.domain;

import java.util.UUID;

public record Usuario(UUID id, String nome, Cpf cpf, String senhaHash) {
    @Override public String toString() { return "Usuario[id=" + id + "]"; }
}
