package br.com.sicredi.desafiovotacao.application.service;

import br.com.sicredi.desafiovotacao.application.port.in.VotacaoUseCase;
import br.com.sicredi.desafiovotacao.application.port.out.VotacaoRepository;
import br.com.sicredi.desafiovotacao.domain.*;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import br.com.sicredi.desafiovotacao.domain.exception.*;

public class VotacaoService implements VotacaoUseCase {
    private final VotacaoRepository repository;
    private final Clock clock;

    public VotacaoService(VotacaoRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Pauta criarPauta(String titulo, String descricao) {
        return repository.salvarPauta(new Pauta(UUID.randomUUID(), titulo, descricao, clock.instant()));
    }

    @Override
    public Pauta consultarPauta(UUID pautaId) {
        return repository.buscarPauta(pautaId).orElseThrow(PautaNaoEncontradaException::new);
    }

    @Override
    public List<Pauta> listarPautas(int pagina, int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) {
            throw new DadosInvalidosException("Página deve ser >= 0 e tamanho entre 1 e 100.");
        }
        return repository.listarPautas(pagina, tamanho);
    }

    @Override
    public SessaoVotacao abrirSessao(UUID pautaId, Integer duracaoMinutos) {
        int minutos = SessaoVotacao.validarDuracao(duracaoMinutos);
        consultarPauta(pautaId);
        return repository.abrirSessao(pautaId, minutos);
    }

    @Override
    public void votar(UUID pautaId, String associadoId, Escolha escolha) {
        Voto voto = new Voto(pautaId, associadoId, escolha);
        consultarPauta(pautaId);
        repository.registrarVoto(voto);
    }

    @Override
    public Resultado consultarResultado(UUID pautaId) {
        return repository.buscarResultado(pautaId).orElseThrow(PautaNaoEncontradaException::new);
    }
}