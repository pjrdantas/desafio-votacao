package br.com.sicredi.desafiovotacao.application.port.out;

public interface SenhaEncoder {
    String codificar(String senha);
    boolean confere(String senha, String hash);
}
