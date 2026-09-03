package br.com.sicredi.desafiovotacao.domain.exception;

public class SessaoJaExistenteException extends RegraNegocioException {
    private static final long serialVersionUID = 1L;

    public SessaoJaExistenteException() {
        super(Codigo.SESSAO_JA_EXISTE, "A pauta já possui uma sessão.");
    }
}