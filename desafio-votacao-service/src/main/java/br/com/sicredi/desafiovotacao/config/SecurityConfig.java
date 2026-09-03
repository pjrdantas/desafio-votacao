package br.com.sicredi.desafiovotacao.config;

import br.com.sicredi.desafiovotacao.application.port.in.AutenticacaoUseCase;
import br.com.sicredi.desafiovotacao.adapter.in.web.security.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

@Configuration
public class SecurityConfig {
    @Bean RSAKey rsaKey(@Value("${app.security.key-store-path}") String caminho) throws Exception {
        return JwtKeyStore.carregar(Path.of(caminho));
    }
    @Bean JwtEncoder jwtEncoder(RSAKey key) { return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key))); }
    @Bean JwtDecoder jwtDecoder(RSAKey key, AutenticacaoUseCase auth, @Value("${app.security.issuer}") String issuer) throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        OAuth2TokenValidator<Jwt> sessao = jwt -> {
            try {
                boolean valida = jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                    && jwt.getAudience().contains("votacao-api")
                    && auth.sessaoAtiva(UUID.fromString(jwt.getClaimAsString("sid")), UUID.fromString(jwt.getSubject()));
                if (valida) return OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException | NullPointerException invalid) { }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Sessão inválida.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ofSeconds(5)),
            new JwtIssuerValidator(issuer), sessao));
        return decoder;
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorWriter errors, Clock clock,
            @Value("${app.security.cookie-secure}") boolean secure,
            @Value("${app.security.auth-requests-per-minute}") int limite) throws Exception {
        var csrf = new CookieCsrfTokenRepository();
        csrf.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(secure).sameSite("Strict").path("/api/v1/auth"));
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
            .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .requireCsrfProtectionMatcher(req -> req.getRequestURI().startsWith("/api/v1/auth/")
                    && !Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod())))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/cadastro", "/api/v1/auth/login",
                    "/api/v1/auth/renovar", "/api/v1/auth/logout").permitAll()
                .requestMatchers("/api/**").hasAuthority("SCOPE_votacao")
                .anyRequest().permitAll())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> errors.write(req, res, 401, "NAO_AUTENTICADO", "Entre para acessar a aplicação."))
                .accessDeniedHandler((req, res, e) -> errors.write(req, res, 403,
                    e instanceof CsrfException ? "CSRF_INVALIDO" : "ACESSO_NEGADO",
                    e instanceof CsrfException ? "Atualize a página e tente novamente." : "Acesso não permitido.")))
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {})
                .authenticationEntryPoint((req, res, e) -> errors.write(req, res, 401, "NAO_AUTENTICADO", "Sua sessão expirou. Entre novamente.")));
        http.addFilterBefore(new AuthRateLimitFilter(clock, limite, errors), CsrfFilter.class);
        return http.build();
    }
}
