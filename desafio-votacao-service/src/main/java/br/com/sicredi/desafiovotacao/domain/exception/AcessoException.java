package br.com.sicredi.desafiovotacao.domain.exception;

public final class AcessoException extends RegraNegocioException {
    public AcessoException(Codigo codigo, String mensagem) { super(codigo, mensagem); }
    public static AcessoException cpfInvalido() { return new AcessoException(Codigo.CPF_INVALIDO, "Informe um CPF válido."); }
    public static AcessoException credenciais() { return new AcessoException(Codigo.CREDENCIAIS_INVALIDAS, "CPF ou senha inválidos."); }
    public static AcessoException sessao() { return new AcessoException(Codigo.NAO_AUTENTICADO, "Sua sessão expirou. Entre novamente."); }
}
