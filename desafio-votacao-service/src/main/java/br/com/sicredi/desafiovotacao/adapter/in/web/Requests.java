package br.com.sicredi.desafiovotacao.adapter.in.web;

import br.com.sicredi.desafiovotacao.domain.Escolha;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public final class Requests {
    private Requests() {}

    @Schema(name = "CriarPautaRequest", description = "Dados para cadastrar uma pauta.")
    public record CriarPauta(
            @NotBlank(message = "O título é obrigatório.")
            @Size(max = 200, message = "O título deve ter até 200 caracteres.")
            @Schema(description = "Título da pauta.", example = "Reforma da sede") String titulo,
            @Size(max = 2000, message = "A descrição deve ter até 2000 caracteres.")
            @Schema(description = "Descrição opcional.", example = "Deliberação sobre o orçamento.") String descricao) {}

    @Schema(name = "AbrirSessaoRequest", description = "Pode ser omitido ou enviado como {} para usar 1 minuto.")
    public record AbrirSessao(
            @Positive(message = "A duração deve ser um inteiro positivo em minutos.")
            @Schema(description = "Duração em minutos; padrão 1 quando omitida ou nula.",
                    example = "5", minimum = "1", defaultValue = "1") Integer duracaoMinutos) {}

    @Schema(name = "RegistrarVotoRequest", description = "Voto imutável do usuário autenticado. O associado é identificado pelo JWT.")
    public record RegistrarVoto(
            @NotNull(message = "A escolha é obrigatória.")
            @Schema(description = "Opção de voto.", example = "SIM", allowableValues = {"SIM", "NAO"}) Escolha escolha) {}

    @Schema(name = "ListarPautasMobileRequest", description = "Página de pautas exibida no mobile.")
    public record ListarPautas(
            @Min(value = 0, message = "A página deve ser maior ou igual a zero.")
            @Schema(description = "Índice da página, começando em zero.",
                    example = "0", minimum = "0", defaultValue = "0") Integer pagina) {}
}