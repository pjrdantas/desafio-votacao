package br.com.sicredi.desafiovotacao.application;

import br.com.sicredi.desafiovotacao.application.port.out.VotacaoRepository;
import br.com.sicredi.desafiovotacao.application.service.VotacaoService;
import br.com.sicredi.desafiovotacao.domain.*;
import org.junit.jupiter.api.Test;
import br.com.sicredi.desafiovotacao.domain.exception.RegraNegocioException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VotacaoServiceTest {
    private final VotacaoRepository repository = mock(VotacaoRepository.class);
    private final Instant agora = Instant.parse("2026-09-02T12:00:00Z");
    private final VotacaoService service = new VotacaoService(repository, Clock.fixed(agora, ZoneOffset.UTC));

    @Test
    void naoGravaQuandoPautaNaoExiste() {
        UUID id = UUID.randomUUID();
        when(repository.buscarPauta(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.votar(id, "1", Escolha.SIM))
                .isInstanceOfSatisfying(RegraNegocioException.class,
                        e -> assertThat(e.codigo()).isEqualTo(RegraNegocioException.Codigo.PAUTA_NAO_ENCONTRADA));
        verify(repository, never()).registrarVoto(any());
    }

    @Test
    void aplicaDuracaoPadraoAntesDeAbrirSessao() {
        UUID id = UUID.randomUUID();
        when(repository.buscarPauta(id)).thenReturn(Optional.of(new Pauta(id, "Pauta", "", agora)));
        service.abrirSessao(id, null);
        verify(repository).abrirSessao(id, 1);
    }

    @Test
    void rejeitaPaginacaoSemConsultarBanco() {
        assertThatThrownBy(() -> service.listarPautas(-1, 20)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.listarPautas(0, 101)).isInstanceOf(RegraNegocioException.class);
        verifyNoInteractions(repository);
    }
}