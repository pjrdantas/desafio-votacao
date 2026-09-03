package br.com.sicredi.desafiovotacao.domain.exception;

public abstract class RegraNegocioException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Codigo {
        DADOS_INVALIDOS, PAUTA_NAO_ENCONTRADA, SESSAO_JA_EXISTE,
        SESSAO_NAO_ABERTA, SESSAO_ENCERRADA, VOTO_DUPLICADO,
        CPF_INVALIDO, CPF_NAO_ENCONTRADO, CPF_JA_CADASTRADO, CREDENCIAIS_INVALIDAS, NAO_AUTENTICADO, UNABLE_TO_VOTE
    }

    private final Codigo codigo;

    protected RegraNegocioException(Codigo codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public Codigo codigo() {
        return codigo;
    }
}
