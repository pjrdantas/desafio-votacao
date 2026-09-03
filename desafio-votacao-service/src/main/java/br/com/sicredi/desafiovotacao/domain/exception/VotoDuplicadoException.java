package br.com.sicredi.desafiovotacao.domain.exception;

public class VotoDuplicadoException extends RegraNegocioException {
    private static final long serialVersionUID = 1L;

    public VotoDuplicadoException() {
        super(Codigo.VOTO_DUPLICADO, "O associado já votou nesta pauta.");
    }
}