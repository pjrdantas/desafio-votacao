package br.com.sicredi.desafiovotacao.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface AutenticacaoUseCase {
    record Perfil(UUID id, String nome, String cpf) {}
    record Acesso(String accessToken, String refreshToken, Instant expiraEm, Instant renovacaoExpiraEm, Perfil usuario) {
        @Override public String toString() { return "Acesso[usuario=" + usuario.id() + "]"; }
    }
    Perfil cadastrar(String nome, String cpf, String senha);
    Acesso entrar(String cpf, String senha);
    Acesso renovar(String refreshToken);
    void sair(String refreshToken);
    Perfil perfil(UUID usuario);
    boolean sessaoAtiva(UUID sessao, UUID usuario);
}
