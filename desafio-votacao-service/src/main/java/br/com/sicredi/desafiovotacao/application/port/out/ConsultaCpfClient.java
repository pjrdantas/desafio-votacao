package br.com.sicredi.desafiovotacao.application.port.out;

public interface ConsultaCpfClient {
    enum Status { ABLE_TO_VOTE, UNABLE_TO_VOTE }
    Status consultar(String cpf);
}
