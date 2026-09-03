package br.com.sicredi.desafiovotacao.application.port.out;

import br.com.sicredi.desafiovotacao.domain.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VotacaoRepository {
    Pauta salvarPauta(Pauta pauta);
    Optional<Pauta> buscarPauta(UUID pautaId);
    List<Pauta> listarPautas(int pagina, int tamanho);
    // A implementação garante unicidade da sessão e usa um relógio compartilhado.
    SessaoVotacao abrirSessao(UUID pautaId, int duracaoMinutos);
    // A implementação garante unicidade e verifica o prazo atomicamente na gravação.
    void registrarVoto(Voto voto);
    // Totais e instante de apuração pertencem ao mesmo snapshot.
    Optional<Resultado> buscarResultado(UUID pautaId);
}