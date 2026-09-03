package br.com.sicredi.desafiovotacao.adapter.in.web.mobile;

import br.com.sicredi.desafiovotacao.adapter.in.web.Requests;
import br.com.sicredi.desafiovotacao.application.port.in.VotacaoUseCase;
import br.com.sicredi.desafiovotacao.application.port.in.VotoAutenticadoUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import br.com.sicredi.desafiovotacao.domain.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.sicredi.desafiovotacao.adapter.in.web.mobile.Tela.*;

@RestController
@RequestMapping(value = "/api/v1/mobile", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Mobile", description = "Contrato proposto a partir do anexo incompleto. Todas as ações utilizam POST.")
@ApiResponse(responseCode = "200", description = "Próxima tela mobile.", useReturnTypeSchema = true)
public class MobileController {
    private static final int TAMANHO_PAGINA = 20;
    private final VotacaoUseCase votacao;
    private final VotoAutenticadoUseCase votoAutenticado;
    private final String baseUrl;

    public MobileController(VotacaoUseCase votacao, VotoAutenticadoUseCase votoAutenticado, @Value("${app.mobile-base-url}") String baseUrl) {
        URI uri = URI.create(baseUrl);
        if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("app.mobile-base-url deve ser uma URL HTTP(S) absoluta.");
        }
        this.votacao = votacao;
        this.votoAutenticado = votoAutenticado;
        this.baseUrl = baseUrl.replaceAll("/+$", "") + "/api/v1/mobile";
    }

    @RequestMapping(method = {RequestMethod.GET, RequestMethod.POST})
    @Operation(summary = "Abrir menu mobile", description = "Entrada do aplicativo; botões retornam a próxima tela via POST.")
    public Tela inicio() {
        return formulario("Assembleia", List.of(),
                acao("Cadastrar pauta", "/pautas/nova"), acao("Ver pautas", "/pautas/listar"));
    }

    @PostMapping("/pautas/nova")
    @Operation(summary = "Exibir formulário de cadastro")
    public Tela novaPauta() {
        return formulario("Cadastrar pauta", List.of(
                        new Campo("titulo", "TEXTO", "Título", true, null, false),
                        new Campo("descricao", "TEXTO", "Descrição", false, null, false)),
                acao("Salvar", "/pautas"), acao("Voltar", ""));
    }

    @PostMapping(value = "/pautas", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cadastrar pauta pelo mobile", description = "Salva os campos e retorna uma tela SELECAO com as ações da pauta.")
    public Tela criar(@Valid @RequestBody Requests.CriarPauta request) {
        Pauta pauta = votacao.criarPauta(request.titulo(), request.descricao());
        return detalhe(pauta.id());
    }

    @PostMapping(value = "/pautas/listar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Listar pautas no mobile", description = "Retorna até 20 pautas e opções de navegação. Página padrão zero.")
    public Tela listar(@Valid @RequestBody(required = false) Requests.ListarPautas request) {
        int pagina = request == null || request.pagina() == null ? 0 : request.pagina();
        List<Pauta> pautas = votacao.listarPautas(pagina, TAMANHO_PAGINA);
        List<Acao> opcoes = new ArrayList<>();
        pautas.forEach(p -> opcoes.add(acao(p.titulo(), "/pautas/" + p.id())));
        if (pagina > 0) {
            opcoes.add(acao("Página anterior", "/pautas/listar", Map.of("pagina", pagina - 1)));
        }
        if (pautas.size() == TAMANHO_PAGINA && pagina < Integer.MAX_VALUE) {
            opcoes.add(acao("Próxima página", "/pautas/listar", Map.of("pagina", pagina + 1)));
        }
        opcoes.add(acao("Menu inicial", ""));
        return selecao(pautas.isEmpty() ? "Nenhuma pauta nesta página" : "Escolha uma pauta", opcoes);
    }

    @PostMapping("/pautas/{id}")
    @Operation(summary = "Exibir ações da pauta")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    public Tela detalhe(@PathVariable UUID id) {
        Pauta pauta = votacao.consultarPauta(id);
        Resultado resultado = votacao.consultarResultado(id);
        String caminho = "/pautas/" + id;
        List<Acao> opcoes = new ArrayList<>();
        if (resultado.situacao() == Resultado.Situacao.NAO_ABERTA) {
            opcoes.add(acao("Abrir sessão", caminho + "/sessao/formulario"));
        } else if (resultado.situacao() == Resultado.Situacao.ABERTA) {
            opcoes.add(acao("Votar", caminho + "/voto/formulario"));
        }
        opcoes.add(acao("Consultar resultado", caminho + "/resultado"));
        opcoes.add(acao("Ver pautas", "/pautas/listar"));
        return selecao(pauta.titulo(), opcoes);
    }

    @PostMapping("/pautas/{id}/sessao/formulario")
    @Operation(summary = "Exibir formulário de abertura de sessão")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    public Tela formularioSessao(@PathVariable UUID id) {
        votacao.consultarPauta(id);
        return formulario("Abrir sessão", List.of(
                        new Campo("duracaoMinutos", "NUMERO", "Duração em minutos", false, 1, false)),
                acao("Abrir", "/pautas/" + id + "/sessao"), acao("Voltar", "/pautas/" + id));
    }

    @PostMapping(value = "/pautas/{id}/sessao", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Abrir sessão pelo mobile", description = "Duração padrão 1 minuto; retorna as ações disponíveis da pauta.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    public Tela abrir(@PathVariable UUID id, @Valid @RequestBody(required = false) Requests.AbrirSessao request) {
        votacao.abrirSessao(id, request == null ? null : request.duracaoMinutos());
        return detalhe(id);
    }

    @PostMapping("/pautas/{id}/voto/formulario")
    @Operation(summary = "Exibir formulário de voto", description = "Usuário autenticado e botões Sim/Não. O prazo é validado ao registrar o voto.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    public Tela formularioVoto(@PathVariable UUID id) {
        Pauta pauta = votacao.consultarPauta(id);
        return formulario("Votar: " + pauta.titulo(), List.of(
                        new Campo("associado", "TEXTO", "Identificação", false, "Usuário autenticado pelo CPF", true)),
                acao("Sim", "/pautas/" + id + "/votos", Map.of("escolha", "SIM")),
                acao("Não", "/pautas/" + id + "/votos", Map.of("escolha", "NAO")));
    }

    @PostMapping(value = "/pautas/{id}/votos", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar voto pelo mobile", description = "Retorna a tela do resultado após registrar o voto.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    public Tela votar(@PathVariable UUID id, @Valid @RequestBody Requests.RegistrarVoto request, @AuthenticationPrincipal Jwt jwt) {
        votoAutenticado.votar(id, UUID.fromString(jwt.getSubject()), request.escolha());
        return resultado(id);
    }

    @PostMapping("/pautas/{id}/resultado")
    @Operation(summary = "Exibir resultado no mobile", description = "Mostra situação, totais e decisão; botões Atualizar e Ver pautas.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    public Tela resultado(@PathVariable UUID id) {
        Resultado resultado = votacao.consultarResultado(id);
        return formulario("Resultado da votação", List.of(
                        leitura("situacao", "Situação", resultado.situacao().name()),
                        leitura("sim", "Sim", resultado.sim()),
                        leitura("nao", "Não", resultado.nao()),
                        leitura("total", "Total", resultado.total()),
                        leitura("decisao", "Decisão", resultado.decisao().name())),
                acao("Atualizar", "/pautas/" + id + "/resultado"), acao("Ver pautas", "/pautas/listar"));
    }

    private Campo leitura(String id, String label, Object valor) {
        return new Campo(id, valor instanceof Number ? "NUMERO" : "TEXTO", label, false, valor, true);
    }

    private Acao acao(String label, String caminho) {
        return acao(label, caminho, Map.of());
    }

    private Acao acao(String label, String caminho, Map<String, Object> body) {
        return new Acao(label, baseUrl + caminho, body);
    }
}