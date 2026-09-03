package br.com.sicredi.desafiovotacao.adapter.in.web.mobile;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Tela mobile proposta. FORMULARIO usa itens/botoes; SELECAO usa opcoes. Listas vazias são omitidas.")
public record Tela(
        @Schema(description = "Tipo da tela.", allowableValues = {"FORMULARIO", "SELECAO"}, example = "FORMULARIO") String tipo,
        @Schema(description = "Título exibido no aplicativo.", example = "Cadastrar pauta") String titulo,
        @Schema(description = "Campos do formulário.") List<Campo> itens,
        @Schema(description = "Um ou dois botões no formulário.") List<Acao> botoes,
        @Schema(description = "Opções da tela de seleção.") List<Acao> opcoes) {
    public static Tela formulario(String titulo, List<Campo> itens, Acao... botoes) {
        return new Tela("FORMULARIO", titulo, List.copyOf(itens), List.of(botoes), List.of());
    }

    public static Tela selecao(String titulo, List<Acao> opcoes) {
        return new Tela("SELECAO", titulo, List.of(), List.of(), List.copyOf(opcoes));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(name = "CampoMobile", description = "Campo de entrada ou leitura; somenteLeitura não deve ser enviado no POST.")
    public record Campo(
            @Schema(description = "Chave usada no corpo da requisição.", example = "titulo") String id,
            @Schema(allowableValues = {"TEXTO", "NUMERO"}, example = "TEXTO") String tipo,
            @Schema(description = "Rótulo exibido.", example = "Identificador do associado") String label,
            @Schema(description = "Preenchimento obrigatório.", example = "true") boolean obrigatorio,
            @Schema(description = "Valor inicial ou de leitura, textual ou numérico.") Object valor,
            @Schema(description = "Indica campo apenas de leitura.", example = "false") boolean somenteLeitura) {}

    @Schema(name = "AcaoMobile", description = "Executar POST na URL, combinando body com os campos editáveis preenchidos.")
    public record Acao(
            @Schema(description = "Rótulo da ação.", example = "Sim") String label,
            @Schema(description = "URL absoluta do callback.", example = "http://localhost:8080/api/v1/mobile/pautas") String url,
            @Schema(description = "Valores fixos da ação.", example = "{\"escolha\":\"SIM\"}") Map<String, Object> body) {}
}