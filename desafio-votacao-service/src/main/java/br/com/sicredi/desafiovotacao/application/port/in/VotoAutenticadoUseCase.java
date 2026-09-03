package br.com.sicredi.desafiovotacao.application.port.in;

import br.com.sicredi.desafiovotacao.domain.Escolha;
import java.util.UUID;

public interface VotoAutenticadoUseCase {
    void votar(UUID pauta, UUID usuario, Escolha escolha);
}
