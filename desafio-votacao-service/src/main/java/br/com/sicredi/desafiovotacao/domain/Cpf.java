package br.com.sicredi.desafiovotacao.domain;

import br.com.sicredi.desafiovotacao.domain.exception.AcessoException;

public record Cpf(String valor) {
    public Cpf {
        if (valor == null || !valor.matches("(?:[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2})")) {
            throw AcessoException.cpfInvalido();
        }
        valor = valor.replace(".", "").replace("-", "");
        if (valor.chars().distinct().count() == 1 || digito(valor, 9) != valor.charAt(9) - '0'
                || digito(valor, 10) != valor.charAt(10) - '0') throw AcessoException.cpfInvalido();
    }

    private static int digito(String cpf, int tamanho) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) soma += (cpf.charAt(i) - '0') * (tamanho + 1 - i);
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    public String mascarado() { return "***." + valor.substring(3, 6) + "." + valor.substring(6, 9) + "-**"; }
    @Override public String toString() { return mascarado(); }
}
