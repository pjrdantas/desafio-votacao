package br.com.sicredi.desafiovotacao.application.service;

import br.com.sicredi.desafiovotacao.application.port.in.*;
import br.com.sicredi.desafiovotacao.application.port.out.*;
import br.com.sicredi.desafiovotacao.domain.Escolha;
import br.com.sicredi.desafiovotacao.domain.exception.*;
import java.util.UUID;

public class VotoAutenticadoService implements VotoAutenticadoUseCase {
    private final UsuarioRepository usuarios;
    private final ConsultaCpfClient cpfClient;
    private final VotacaoUseCase votacao;
    public VotoAutenticadoService(UsuarioRepository usuarios, ConsultaCpfClient cpfClient, VotacaoUseCase votacao) {
        this.usuarios = usuarios; this.cpfClient = cpfClient; this.votacao = votacao;
    }
    public void votar(UUID pauta, UUID usuario, Escolha escolha) {
        var associado = usuarios.buscarId(usuario).orElseThrow(AcessoException::sessao);
        if (cpfClient.consultar(associado.cpf().valor()) == ConsultaCpfClient.Status.UNABLE_TO_VOTE)
            throw new AcessoException(RegraNegocioException.Codigo.UNABLE_TO_VOTE, "A consulta de CPF não autorizou este voto. Tente novamente mais tarde.");
        votacao.votar(pauta, associado.id().toString(), escolha);
    }
}
