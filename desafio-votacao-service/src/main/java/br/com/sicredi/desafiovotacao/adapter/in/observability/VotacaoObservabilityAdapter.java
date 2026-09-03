package br.com.sicredi.desafiovotacao.adapter.in.observability;

import br.com.sicredi.desafiovotacao.application.port.in.VotacaoUseCase;
import br.com.sicredi.desafiovotacao.domain.*;
import br.com.sicredi.desafiovotacao.domain.exception.RegraNegocioException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class VotacaoObservabilityAdapter implements VotacaoUseCase {
    private static final Logger log = LoggerFactory.getLogger(VotacaoObservabilityAdapter.class);
    private final VotacaoUseCase delegate;
    private final MeterRegistry registry;

    public VotacaoObservabilityAdapter(VotacaoUseCase delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public Pauta criarPauta(String titulo, String descricao) {
        return medir("criar_pauta", () -> delegate.criarPauta(titulo, descricao));
    }

    @Override
    public Pauta consultarPauta(UUID pautaId) {
        return medir("consultar_pauta", () -> delegate.consultarPauta(pautaId));
    }

    @Override
    public List<Pauta> listarPautas(int pagina, int tamanho) {
        return medir("listar_pautas", () -> delegate.listarPautas(pagina, tamanho));
    }

    @Override
    public SessaoVotacao abrirSessao(UUID pautaId, Integer duracaoMinutos) {
        return medir("abrir_sessao", () -> delegate.abrirSessao(pautaId, duracaoMinutos));
    }

    @Override
    public void votar(UUID pautaId, String associadoId, Escolha escolha) {
        medir("votar", () -> {
            delegate.votar(pautaId, associadoId, escolha);
            return null;
        });
    }

    @Override
    public Resultado consultarResultado(UUID pautaId) {
        return medir("consultar_resultado", () -> delegate.consultarResultado(pautaId));
    }

    private <T> T medir(String operacao, Supplier<T> action) {
        Timer.Sample sample = Timer.start(registry);
        String resultado = "sucesso";
        String motivo = "NENHUM";
        try {
            return action.get();
        } catch (RegraNegocioException exception) {
            resultado = "rejeitada";
            motivo = exception.codigo().name();
            throw exception;
        } catch (RuntimeException exception) {
            resultado = "erro";
            motivo = "FALHA_TECNICA";
            throw exception;
        } finally {
            sample.stop(Timer.builder("votacao.operacoes")
                    .description("Quantidade e duração das operações de votação")
                    .tags("operacao", operacao, "resultado", resultado, "motivo", motivo)
                    .register(registry));
            Level level = switch (resultado) {
                case "rejeitada" -> Level.WARN;
                case "erro" -> Level.ERROR;
                default -> Level.INFO;
            };
            log.atLevel(level)
                    .addKeyValue("operacao", operacao)
                    .addKeyValue("resultado", resultado)
                    .addKeyValue("motivo", motivo)
                    .log("Operação {} concluída: {} ({}).", operacao, resultado, motivo);
        }
    }
}
