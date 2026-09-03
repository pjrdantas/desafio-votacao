package br.com.sicredi.desafiovotacao.domain;

import org.junit.jupiter.api.Test;
import br.com.sicredi.desafiovotacao.domain.exception.RegraNegocioException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class RegrasVotacaoTest {
    private final UUID pautaId = UUID.randomUUID();
    private final Instant inicio = Instant.parse("2026-09-02T12:00:00Z");
    private final SessaoVotacao sessao = new SessaoVotacao(pautaId, inicio, inicio.plusSeconds(60));

    @Test
    void aceitaInicioMasRejeitaInstanteExatoDoEncerramento() {
        assertThat(sessao.abertaEm(inicio.minusNanos(1))).isFalse();
        assertThat(sessao.abertaEm(inicio)).isTrue();
        assertThat(sessao.abertaEm(inicio.plusSeconds(60).minusNanos(1))).isTrue();
        assertThat(sessao.abertaEm(inicio.plusSeconds(60))).isFalse();
    }

    @Test
    void duracaoPadraoUmMinutoERejeitaValoresNaoPositivos() {
        assertThat(SessaoVotacao.validarDuracao(null)).isEqualTo(1);
        assertThat(SessaoVotacao.validarDuracao(5)).isEqualTo(5);
        assertThatThrownBy(() -> SessaoVotacao.validarDuracao(0)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> SessaoVotacao.validarDuracao(-1)).isInstanceOf(RegraNegocioException.class);
    }

    @ParameterizedTest
    @CsvSource({"0,0,SEM_VOTOS", "1,1,EMPATE", "2,1,APROVADA", "1,2,REJEITADA"})
    void apuraResultadoFinal(long sim, long nao, Resultado.Decisao decisao) {
        Resultado resultado = Resultado.apurar(pautaId, sessao, sim, nao, inicio.plusSeconds(60));
        assertThat(resultado.situacao()).isEqualTo(Resultado.Situacao.ENCERRADA);
        assertThat(resultado.decisao()).isEqualTo(decisao);
        assertThat(resultado.total()).isEqualTo(sim + nao);
    }

    @Test
    void resultadoParcialNaoDeclaraVencedor() {
        Resultado resultado = Resultado.apurar(pautaId, sessao, 10, 1, inicio.plusSeconds(30));
        assertThat(resultado.situacao()).isEqualTo(Resultado.Situacao.ABERTA);
        assertThat(resultado.decisao()).isEqualTo(Resultado.Decisao.PENDENTE);
    }

    @Test
    void pautaSemSessaoPossuiResultadoPendente() {
        Resultado resultado = Resultado.apurar(pautaId, null, 0, 0, inicio);
        assertThat(resultado.situacao()).isEqualTo(Resultado.Situacao.NAO_ABERTA);
        assertThat(resultado.decisao()).isEqualTo(Resultado.Decisao.PENDENTE);
    }

    @Test
    void validaENormalizaDadosDaPautaEDoVoto() {
        assertThat(new Pauta(pautaId, "  Pauta  ", null, inicio).titulo()).isEqualTo("Pauta");
        assertThat(new Voto(pautaId, "  associado  ", Escolha.SIM).associadoId()).isEqualTo("associado");
        assertThatThrownBy(() -> new Pauta(pautaId, " ", null, inicio)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new Pauta(pautaId, "a".repeat(201), null, inicio)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new Voto(pautaId, " ", Escolha.NAO)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> new Voto(pautaId, "1", null)).isInstanceOf(RegraNegocioException.class);
    }
}