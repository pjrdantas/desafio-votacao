package br.com.sicredi.desafiovotacao.adapter.in.web;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void limparMdc() {
        MDC.clear();
    }

    @Test
    void propagaIdentificadorValidoELimpaMdcAposRequisicao() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.HEADER, "requisicao-123");
        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("correlationId")).isEqualTo("requisicao-123");
            assertThat(req.getAttribute(CorrelationIdFilter.ATTRIBUTE)).isEqualTo("requisicao-123");
        });
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("requisicao-123");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void geraUuidQuandoHeaderEhInvalido() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.HEADER, "identificador\r\ninvalido");
        filter.doFilter(request, response, (req, res) -> {});
        assertThatCode(() -> UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER))).doesNotThrowAnyException();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void restauraContextoAnteriorMesmoSeCadeiaFalhar() {
        MDC.put("correlationId", "contexto-anterior");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("falha simulada");
        })).isInstanceOf(ServletException.class);
        assertThat(MDC.get("correlationId")).isEqualTo("contexto-anterior");
    }
}