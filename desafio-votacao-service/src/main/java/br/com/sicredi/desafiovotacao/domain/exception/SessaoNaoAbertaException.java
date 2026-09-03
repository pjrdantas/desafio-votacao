package br.com.sicredi.desafiovotacao.domain.exception;

public class SessaoNaoAbertaException extends RegraNegocioException {
    private static final long serialVersionUID = 1L;

    public SessaoNaoAbertaException() {
        super(Codigo.SESSAO_NAO_ABERTA, "A pauta ainda não possui sessão aberta.");
    }
}