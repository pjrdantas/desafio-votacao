package br.com.sicredi.desafiovotacao.adapter.in.web;

import br.com.sicredi.desafiovotacao.application.port.in.AutenticacaoUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação")
public class AutenticacaoController {
    private static final String COOKIE = "VOTACAO_REFRESH";
    private final AutenticacaoUseCase auth;
    private final Clock clock;
    private final boolean secure;
    public AutenticacaoController(AutenticacaoUseCase auth, Clock clock, @Value("${app.security.cookie-secure}") boolean secure) {
        this.auth = auth; this.clock = clock; this.secure = secure;
    }
    public record Cadastro(@NotBlank @Size(max=120) String nome, @NotBlank @Size(max=14) String cpf,
            @NotBlank @Size(min=10,max=72) String senha) {}
    public record Login(@NotBlank @Size(max=14) String cpf, @NotBlank @Size(max=72) String senha) {}
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, AutenticacaoUseCase.Perfil usuario) {}

    @GetMapping("/csrf")
    @Operation(summary="Obter proteção CSRF para autenticação")
    public ResponseEntity<Map<String,String>> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }
    @PostMapping(value="/cadastro", consumes=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Cadastrar usuário com CPF válido")
    @ApiResponse(responseCode="201", description="Conta cadastrada.", useReturnTypeSchema=true)
    @ApiResponse(responseCode="409", ref="#/components/responses/Conflict")
    public ResponseEntity<AutenticacaoUseCase.Perfil> cadastrar(@Valid @RequestBody Cadastro request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
            .body(auth.cadastrar(request.nome(), request.cpf(), request.senha()));
    }
    @PostMapping(value="/login", consumes=MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary="Entrar com CPF e senha")
    public ResponseEntity<TokenResponse> entrar(@Valid @RequestBody Login request) { return resposta(auth.entrar(request.cpf(), request.senha())); }
    @PostMapping("/renovar")
    @Operation(summary="Renovar JWT e rotacionar cookie de sessão")
    public ResponseEntity<TokenResponse> renovar(@CookieValue(name=COOKIE, required=false) String refresh) {
        return resposta(auth.renovar(refresh));
    }
    @PostMapping("/logout")
    @Operation(summary="Encerrar sessão e revogar seus tokens")
    @ApiResponse(responseCode="204", description="Sessão encerrada.", content=@Content)
    public ResponseEntity<Void> sair(@CookieValue(name=COOKIE, required=false) String refresh) {
        auth.sair(refresh);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO)).build();
    }
    @GetMapping("/me")
    @Operation(summary="Consultar usuário autenticado")
    public ResponseEntity<AutenticacaoUseCase.Perfil> perfil(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.perfil(UUID.fromString(jwt.getSubject())));
    }
    private ResponseEntity<TokenResponse> resposta(AutenticacaoUseCase.Acesso acesso) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE, cookie(acesso.refreshToken(), Duration.between(clock.instant(), acesso.renovacaoExpiraEm())))
            .body(new TokenResponse(acesso.accessToken(), "Bearer", Math.max(0, Duration.between(clock.instant(), acesso.expiraEm()).toSeconds()), acesso.usuario()));
    }
    private String cookie(String token, Duration idade) {
        return ResponseCookie.from(COOKIE, token).httpOnly(true).secure(secure).sameSite("Strict")
            .path("/api/v1/auth").maxAge(idade).build().toString();
    }
}
