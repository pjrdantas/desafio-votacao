package br.com.sicredi.desafiovotacao.adapter.in.observability;

import br.com.sicredi.desafiovotacao.application.port.in.VotacaoUseCase;
import br.com.sicredi.desafiovotacao.domain.Escolha;
import br.com.sicredi.desafiovotacao.domain.exception.VotoDuplicadoException;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VotacaoObservabilityAdapterTest {
    private final VotacaoUseCase delegate = mock(VotacaoUseCase.class);
    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private final VotacaoObservabilityAdapter adapter = new VotacaoObservabilityAdapter(delegate, registry);

    @AfterEach
    void fecharRegistry() {
        registry.close();
    }

    @Test
    void medeDuracaoEVotosAceitosSemDimensoesPorAssociadoOuPauta() {
        UUID id = UUID.randomUUID();
        doAnswer(invocation -> { clock.add(Duration.ofMillis(250)); return null; })
                .when(delegate).votar(id, "associado-confidencial", Escolha.SIM);

        adapter.votar(id, "associado-confidencial", Escolha.SIM);

        var timer = registry.get("votacao.operacoes").tags("operacao", "votar", "resultado", "sucesso").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250);
        assertThat(timer.getId().getTags()).extracting(tag -> tag.getKey())
                .containsExactlyInAnyOrder("operacao", "resultado", "motivo");
        assertThat(timer.getId().toString()).doesNotContain(id.toString(), "associado-confidencial", "SIM");
        verify(delegate).votar(id, "associado-confidencial", Escolha.SIM);
    }

    @Test
    void rejeicaoNaoContaSucessoEPreservaExcecaoOriginal() {
        UUID id = UUID.randomUUID();
        var exception = new VotoDuplicadoException();
        doThrow(exception).when(delegate).votar(id, "1", Escolha.NAO);

        assertThatThrownBy(() -> adapter.votar(id, "1", Escolha.NAO)).isSameAs(exception);

        assertThat(registry.get("votacao.operacoes")
                .tags("resultado", "rejeitada", "motivo", "VOTO_DUPLICADO").timer().count()).isEqualTo(1);
        assertThat(registry.find("votacao.operacoes").tag("resultado", "sucesso").timer()).isNull();
    }

    @Test
    void falhaTecnicaNaoVazaMensagemEmDimensoesEPreservaExcecao() {
        UUID id = UUID.randomUUID();
        var exception = new IllegalStateException("detalhe-confidencial");
        when(delegate.consultarResultado(id)).thenThrow(exception);

        assertThatThrownBy(() -> adapter.consultarResultado(id)).isSameAs(exception);

        var timer = registry.get("votacao.operacoes")
                .tags("resultado", "erro", "motivo", "FALHA_TECNICA").timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.getId().toString()).doesNotContain("detalhe-confidencial");
    }
}
