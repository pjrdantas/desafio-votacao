package br.com.sicredi.desafiovotacao.application.port.in;

import br.com.sicredi.desafiovotacao.domain.*;
import java.util.List;
import java.util.UUID;

public interface VotacaoUseCase {
    Pauta criarPauta(String titulo, String descricao);
    Pauta consultarPauta(UUID pautaId);
    List<Pauta> listarPautas(int pagina, int tamanho);
    SessaoVotacao abrirSessao(UUID pautaId, Integer duracaoMinutos);
    void votar(UUID pautaId, String associadoId, Escolha escolha);
    Resultado consultarResultado(UUID pautaId);
}