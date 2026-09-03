package br.com.sicredi.desafiovotacao.domain;
import org.junit.jupiter.api.Test;
import br.com.sicredi.desafiovotacao.domain.exception.AcessoException;
import static org.assertj.core.api.Assertions.*;

class CpfTest {
    @Test void normalizaEMascara() {
        assertThat(new Cpf("529.982.247-25").valor()).isEqualTo("52998224725");
        assertThat(new Cpf("52998224725").toString()).doesNotContain("52998224725");
    }
    @Test void rejeitaDigitosInvalidosRepeticaoECaracteresExtras() {
        for (String cpf : new String[]{"11111111111", "00000000000", "52998224724", "52998224715",
                "123", "529a98224725", "529.982.247-25!", "", " 52998224725 "}) {
            assertThatThrownBy(() -> new Cpf(cpf)).as(cpf).isInstanceOf(AcessoException.class);
        }
        assertThatThrownBy(() -> new Cpf(null)).isInstanceOf(AcessoException.class);
    }
}
