package br.com.sicredi.desafiovotacao.config;

import br.com.sicredi.desafiovotacao.adapter.in.observability.VotacaoObservabilityAdapter;
import br.com.sicredi.desafiovotacao.application.port.in.*;
import br.com.sicredi.desafiovotacao.application.port.out.*;
import br.com.sicredi.desafiovotacao.application.service.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class ApplicationConfig {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    VotacaoUseCase votacaoUseCase(VotacaoRepository repository, Clock clock, MeterRegistry registry) {
        return new VotacaoObservabilityAdapter(new VotacaoService(repository, clock), registry);
    }

    @Bean
    AutenticacaoUseCase autenticacaoUseCase(UsuarioRepository usuarios, SessaoAcessoRepository sessoes,
            SenhaEncoder senhas, TokenAcessoEncoder tokens, Clock clock,
            @Value("${app.security.access-duration}") Duration acesso,
            @Value("${app.security.refresh-duration}") Duration renovacao) {
        return new AutenticacaoService(usuarios, sessoes, senhas, tokens, clock, acesso, renovacao);
    }

    @Bean
    VotoAutenticadoUseCase votoAutenticadoUseCase(UsuarioRepository usuarios, ConsultaCpfClient client, VotacaoUseCase votacao) {
        return new VotoAutenticadoService(usuarios, client, votacao);
    }
}
