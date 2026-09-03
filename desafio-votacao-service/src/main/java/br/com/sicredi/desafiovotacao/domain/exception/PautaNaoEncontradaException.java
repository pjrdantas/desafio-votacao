package br.com.sicredi.desafiovotacao.domain.exception;

public class PautaNaoEncontradaException extends RegraNegocioException {
    private static final long serialVersionUID = 1L;

    public PautaNaoEncontradaException() {
        super(Codigo.PAUTA_NAO_ENCONTRADA, "Pauta não encontrada.");
    }
}