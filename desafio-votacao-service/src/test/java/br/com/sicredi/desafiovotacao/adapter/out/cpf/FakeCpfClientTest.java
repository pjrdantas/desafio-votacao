package br.com.sicredi.desafiovotacao.adapter.out.cpf;
import org.junit.jupiter.api.Test;
import br.com.sicredi.desafiovotacao.application.port.out.ConsultaCpfClient.Status;
import br.com.sicredi.desafiovotacao.domain.exception.RegraNegocioException;
import static org.assertj.core.api.Assertions.*;
class FakeCpfClientTest {
    @Test void simulaAmbasRespostasParaCpfValido() {
        assertThat(new FakeCpfClient(() -> true).consultar("52998224725")).isEqualTo(Status.ABLE_TO_VOTE);
        assertThat(new FakeCpfClient(() -> false).consultar("52998224725")).isEqualTo(Status.UNABLE_TO_VOTE);
    }
    @Test void cpfInvalidoTemSemanticaDeNaoEncontrado() {
        assertThatThrownBy(() -> new FakeCpfClient(() -> true).consultar("11111111111"))
            .isInstanceOfSatisfying(RegraNegocioException.class, e -> assertThat(e.codigo().name()).isEqualTo("CPF_NAO_ENCONTRADO"));
    }
}
