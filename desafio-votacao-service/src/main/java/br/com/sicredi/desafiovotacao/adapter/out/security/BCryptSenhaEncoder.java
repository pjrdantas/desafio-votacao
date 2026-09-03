package br.com.sicredi.desafiovotacao.adapter.out.security;

import br.com.sicredi.desafiovotacao.application.port.out.SenhaEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptSenhaEncoder implements SenhaEncoder {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    public String codificar(String senha) { return encoder.encode(senha); }
    public boolean confere(String senha, String hash) { return encoder.matches(senha, hash); }
}
