package br.com.sicredi.desafiovotacao.adapter.out.security;

import br.com.sicredi.desafiovotacao.application.port.out.TokenAcessoEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
public class JwtTokenAcessoEncoder implements TokenAcessoEncoder {
    private final JwtEncoder encoder;
    private final String issuer;
    public JwtTokenAcessoEncoder(JwtEncoder encoder, @Value("${app.security.issuer}") String issuer) {
        this.encoder = encoder; this.issuer = issuer;
    }
    public String emitir(UUID usuario, UUID sessao, Instant agora, Instant expiraEm) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(usuario.toString()).audience(List.of("votacao-api"))
            .issuedAt(agora).expiresAt(expiraEm).id(UUID.randomUUID().toString())
            .claim("sid", sessao.toString()).claim("scope", "votacao").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
