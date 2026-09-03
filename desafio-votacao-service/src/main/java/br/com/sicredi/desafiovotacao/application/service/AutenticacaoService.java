package br.com.sicredi.desafiovotacao.application.service;

import br.com.sicredi.desafiovotacao.application.port.in.AutenticacaoUseCase;
import br.com.sicredi.desafiovotacao.application.port.out.*;
import br.com.sicredi.desafiovotacao.domain.*;
import br.com.sicredi.desafiovotacao.domain.exception.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;

public class AutenticacaoService implements AutenticacaoUseCase {
    private final UsuarioRepository usuarios;
    private final SessaoAcessoRepository sessoes;
    private final SenhaEncoder senhas;
    private final TokenAcessoEncoder tokens;
    private final Clock clock;
    private final Duration duracaoAcesso;
    private final Duration duracaoRenovacao;
    private final SecureRandom random = new SecureRandom();
    private final String hashAusente;

    public AutenticacaoService(UsuarioRepository usuarios, SessaoAcessoRepository sessoes, SenhaEncoder senhas,
            TokenAcessoEncoder tokens, Clock clock, Duration duracaoAcesso, Duration duracaoRenovacao) {
        this.usuarios = usuarios; this.sessoes = sessoes; this.senhas = senhas; this.tokens = tokens;
        this.clock = clock; this.duracaoAcesso = duracaoAcesso; this.duracaoRenovacao = duracaoRenovacao;
        this.hashAusente = senhas.codificar(UUID.randomUUID().toString());
    }

    @Override public Perfil cadastrar(String nome, String cpf, String senha) {
        Cpf documento = new Cpf(cpf);
        if (nome == null || nome.isBlank() || nome.length() > 120) throw new DadosInvalidosException("Informe um nome de até 120 caracteres.");
        if (senha == null || senha.length() < 10 || senha.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new DadosInvalidosException("A senha deve ter ao menos 10 caracteres e até 72 bytes em UTF-8.");
        return perfil(usuarios.salvar(new Usuario(UUID.randomUUID(), nome.trim(), documento, senhas.codificar(senha))));
    }

    @Override public Acesso entrar(String cpf, String senha) {
        Cpf documento = new Cpf(cpf);
        Usuario usuario = usuarios.buscarCpf(documento.valor()).orElse(null);
        boolean correta = senha != null && senha.getBytes(StandardCharsets.UTF_8).length <= 72
                && senhas.confere(senha, usuario == null ? hashAusente : usuario.senhaHash());
        if (usuario == null) throw AcessoException.credenciais();
        if (usuarios.bloqueado(usuario.id(), clock.instant())) throw AcessoException.credenciais();
        if (!correta) { usuarios.registrarFalha(usuario.id(), clock.instant()); throw AcessoException.credenciais(); }
        usuarios.limparFalhas(usuario.id());
        UUID sessao = UUID.randomUUID();
        String refresh = segredo(sessao);
        Instant fim = clock.instant().plus(duracaoRenovacao);
        sessoes.criar(sessao, usuario.id(), hash(refresh), fim);
        return acesso(usuario, sessao, refresh, fim);
    }

    @Override public Acesso renovar(String refreshToken) {
        UUID sessao = sessao(refreshToken);
        String proximo = segredo(sessao);
        var renovacao = sessoes.rotacionar(sessao, hash(refreshToken), hash(proximo), clock.instant())
                .orElseThrow(AcessoException::sessao);
        Usuario usuario = usuarios.buscarId(renovacao.usuarioId()).orElseThrow(AcessoException::sessao);
        return acesso(usuario, sessao, proximo, renovacao.expiraEm());
    }

    @Override public void sair(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            try { sessoes.revogar(sessao(refreshToken), hash(refreshToken)); } catch (AcessoException ignored) { }
        }
    }
    @Override public Perfil perfil(UUID usuario) { return perfil(usuarios.buscarId(usuario).orElseThrow(AcessoException::sessao)); }
    @Override public boolean sessaoAtiva(UUID sessao, UUID usuario) { return sessoes.ativa(sessao, usuario, clock.instant()); }
    private Perfil perfil(Usuario usuario) { return new Perfil(usuario.id(), usuario.nome(), usuario.cpf().mascarado()); }
    private Acesso acesso(Usuario usuario, UUID sessao, String refresh, Instant fim) {
        Instant agora = clock.instant(), expira = agora.plus(duracaoAcesso);
        return new Acesso(tokens.emitir(usuario.id(), sessao, agora, expira), refresh, expira, fim, perfil(usuario));
    }
    private String segredo(UUID sessao) {
        byte[] bytes = new byte[48]; random.nextBytes(bytes);
        return sessao + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private UUID sessao(String token) {
        if (token == null || !token.matches("[a-f0-9-]{36}\\.[A-Za-z0-9_-]{64}")) throw AcessoException.sessao();
        try { return UUID.fromString(token.substring(0, 36)); } catch (IllegalArgumentException e) { throw AcessoException.sessao(); }
    }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
