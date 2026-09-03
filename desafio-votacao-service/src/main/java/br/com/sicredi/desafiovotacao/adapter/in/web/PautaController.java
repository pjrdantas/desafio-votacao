package br.com.sicredi.desafiovotacao.adapter.in.web;

import br.com.sicredi.desafiovotacao.adapter.in.web.response.PautaResponse;
import br.com.sicredi.desafiovotacao.adapter.in.web.response.ResultadoResponse;
import br.com.sicredi.desafiovotacao.adapter.in.web.response.SessaoResponse;
import br.com.sicredi.desafiovotacao.application.port.in.VotacaoUseCase;
import br.com.sicredi.desafiovotacao.application.port.in.VotoAutenticadoUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import br.com.sicredi.desafiovotacao.domain.Pauta;
import br.com.sicredi.desafiovotacao.domain.SessaoVotacao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/v1/pautas", produces = MediaType.APPLICATION_JSON_VALUE)
public class PautaController {
    private static final Logger log = LoggerFactory.getLogger(PautaController.class);
    private final VotacaoUseCase votacao;
    private final VotoAutenticadoUseCase votoAutenticado;

    public PautaController(VotacaoUseCase votacao, VotoAutenticadoUseCase votoAutenticado) {
        this.votacao = votacao;
        this.votoAutenticado = votoAutenticado;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cadastrar uma pauta", operationId = "criarPauta", tags = "Pautas",
            description = "Cria uma pauta com título obrigatório e descrição opcional.")
    @ApiResponse(responseCode = "201", description = "Pauta criada.", useReturnTypeSchema = true,
            headers = @Header(name = "Location", description = "URL relativa da pauta criada.",
                    schema = @Schema(type = "string")))
    public ResponseEntity<PautaResponse> criar(@Valid @RequestBody Requests.CriarPauta request) {
        Pauta pauta = votacao.criarPauta(request.titulo(), request.descricao());
        log.info("Pauta cadastrada: {}", pauta.id());
        return ResponseEntity.created(URI.create("/api/v1/pautas/" + pauta.id())).body(PautaResponse.from(pauta));
    }

    @GetMapping
    @Operation(summary = "Listar pautas", operationId = "listarPautas", tags = "Pautas",
            description = "Retorna pautas por criação decrescente. Uma página vazia retorna uma lista vazia.")
    @ApiResponse(responseCode = "200", description = "Página de pautas.", useReturnTypeSchema = true)
    public List<PautaResponse> listar(
            @Parameter(description = "Índice da página.", schema = @Schema(minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int pagina,
            @Parameter(description = "Quantidade por página.", schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") int tamanho) {
        return votacao.listarPautas(pagina, tamanho).stream().map(PautaResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar uma pauta", operationId = "consultarPauta", tags = "Pautas")
    @ApiResponse(responseCode = "200", description = "Pauta encontrada.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    public PautaResponse consultar(@PathVariable UUID id) {
        return PautaResponse.from(votacao.consultarPauta(id));
    }

    @PostMapping(value = "/{id}/sessao", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Abrir sessão de votação", operationId = "abrirSessao", tags = "Sessões",
            description = "Abre a única sessão da pauta. Duração inteira positiva em minutos, padrão 1. Não permite reabertura.")
    @ApiResponse(responseCode = "201", description = "Sessão aberta.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    public ResponseEntity<SessaoResponse> abrir(@PathVariable UUID id,
            @Valid @RequestBody(required = false) Requests.AbrirSessao request) {
        SessaoVotacao sessao = votacao.abrirSessao(id, request == null ? null : request.duracaoMinutos());
        log.info("Sessão aberta para pauta {}, encerramento {}", id, sessao.encerraEm());
        return ResponseEntity.status(201).body(SessaoResponse.from(sessao));
    }

    @PostMapping(value = "/{id}/votos", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrar um voto", operationId = "registrarVoto", tags = "Votos",
            description = "Aceita SIM ou NAO durante a sessão. Cada associado vota uma única vez por pauta, inclusive sob concorrência.")
    @ApiResponse(responseCode = "201", description = "Voto registrado. Resposta sem corpo.", content = @Content)
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    public ResponseEntity<Void> votar(@PathVariable UUID id,
                                      @Valid @RequestBody Requests.RegistrarVoto request, @AuthenticationPrincipal Jwt jwt) {
        votoAutenticado.votar(id, UUID.fromString(jwt.getSubject()), request.escolha());
        return ResponseEntity.status(201).build();
    }

    @GetMapping("/{id}/resultado")
    @Operation(summary = "Consultar resultado", operationId = "consultarResultado", tags = "Resultados",
            description = "Mostra totais e situação. A decisão permanece PENDENTE até o encerramento da sessão.")
    @ApiResponse(responseCode = "200", description = "Apuração da pauta.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    public ResultadoResponse resultado(@PathVariable UUID id) {
        return ResultadoResponse.from(votacao.consultarResultado(id));
    }
}