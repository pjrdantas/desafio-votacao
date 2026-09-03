package br.com.sicredi.desafiovotacao.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessaoAcessoRepository {
    record Renovacao(UUID usuarioId, Instant expiraEm) {}
    void criar(UUID id, UUID usuario, String hash, Instant expiraEm);
    Optional<Renovacao> rotacionar(UUID id, String hash, String novoHash, Instant agora);
    boolean ativa(UUID id, UUID usuario, Instant agora);
    void revogar(UUID id, String hash);
}
