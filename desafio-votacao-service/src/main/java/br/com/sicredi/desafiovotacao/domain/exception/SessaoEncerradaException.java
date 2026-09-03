package br.com.sicredi.desafiovotacao.domain.exception;

public class SessaoEncerradaException extends RegraNegocioException {
    private static final long serialVersionUID = 1L;

    public SessaoEncerradaException() {
        super(Codigo.SESSAO_ENCERRADA, "Sessão encerrada.");
    }
}