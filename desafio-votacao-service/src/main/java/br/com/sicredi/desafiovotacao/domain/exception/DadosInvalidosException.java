package br.com.sicredi.desafiovotacao.domain.exception;

public class DadosInvalidosException extends RegraNegocioException {
    private static final long serialVersionUID = 1L;

    public DadosInvalidosException(String mensagem) {
        super(Codigo.DADOS_INVALIDOS, mensagem);
    }
}