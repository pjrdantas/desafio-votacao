package br.com.sicredi.desafiovotacao.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface TokenAcessoEncoder {
    String emitir(UUID usuario, UUID sessao, Instant agora, Instant expiraEm);
}
