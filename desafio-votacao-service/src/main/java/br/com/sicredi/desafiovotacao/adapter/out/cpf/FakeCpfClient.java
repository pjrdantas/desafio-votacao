package br.com.sicredi.desafiovotacao.adapter.out.cpf;

import br.com.sicredi.desafiovotacao.application.port.out.ConsultaCpfClient;
import br.com.sicredi.desafiovotacao.domain.Cpf;
import br.com.sicredi.desafiovotacao.domain.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

@Component
public class FakeCpfClient implements ConsultaCpfClient {
    private final BooleanSupplier apto;
    @org.springframework.beans.factory.annotation.Autowired
    public FakeCpfClient(@Value("${app.cpf-fake.modo:aleatorio}") String modo) {
        this(switch (modo) {
            case "aleatorio" -> () -> ThreadLocalRandom.current().nextBoolean();
            case "apto" -> () -> true;
            case "inapto" -> () -> false;
            default -> throw new IllegalArgumentException("Modo CPF fake inválido.");
        });
    }
    public FakeCpfClient(BooleanSupplier apto) { this.apto = apto; }
    @Override public Status consultar(String cpf) {
        try { new Cpf(cpf); } catch (AcessoException invalid) {
            throw new AcessoException(RegraNegocioException.Codigo.CPF_NAO_ENCONTRADO, "CPF inválido na consulta de elegibilidade.");
        }
        return apto.getAsBoolean() ? Status.ABLE_TO_VOTE : Status.UNABLE_TO_VOTE;
    }
}
