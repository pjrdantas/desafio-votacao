package br.com.sicredi.desafiovotacao.adapter.in.web.exception;

import br.com.sicredi.desafiovotacao.domain.exception.RegraNegocioException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.Clock;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler extends BaseApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public ApiExceptionHandler(Clock clock) {
        super(clock);
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<Object> handleBusiness(RegraNegocioException exception, WebRequest request) {
        HttpStatus status = switch (exception.codigo()) {
            case CPF_INVALIDO, DADOS_INVALIDOS -> HttpStatus.BAD_REQUEST;
            case CPF_NAO_ENCONTRADO, UNABLE_TO_VOTE, PAUTA_NAO_ENCONTRADA -> HttpStatus.NOT_FOUND;
            case CREDENCIAIS_INVALIDAS, NAO_AUTENTICADO -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.CONFLICT;
        };
        return buildError(status, exception.codigo().name(), exception.getMessage(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiFieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
                .distinct().toList();
        return buildError(status, "VALIDATION_ERROR", "Dados de entrada inválidos.", request, fields, headers);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName() : exception.getPropertyName();
        List<ApiFieldError> fields = field == null ? List.of()
                : List.of(new ApiFieldError(field, "Formato do parâmetro inválido."));
        return buildError(status, "VALIDATION_ERROR", "Parâmetro da requisição inválido.", request, fields, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return buildError(status, "INVALID_REQUEST_BODY",
                "Corpo da requisição inválido. Verifique os tipos e valores permitidos.",
                request, List.of(), headers);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException exception, WebRequest request) {
        List<ApiFieldError> fields = exception.getConstraintViolations().stream()
                .map(error -> new ApiFieldError(error.getPropertyPath().toString(), error.getMessage()))
                .toList();
        return buildError(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dados de entrada inválidos.",
                request, fields, HttpHeaders.EMPTY);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataConflict(DataIntegrityViolationException exception, WebRequest request) {
        log.warn("Operação rejeitada por restrição de integridade.");
        return buildError(HttpStatus.CONFLICT, "DATA_INTEGRITY_CONFLICT",
                "A operação conflita com os dados existentes.", request);
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<Object> handleUnavailable(DataAccessResourceFailureException exception, WebRequest request) {
        log.error("Falha de acesso à persistência.", exception);
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "Serviço temporariamente indisponível. Tente novamente mais tarde.", request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String code = switch (status.value()) {
            case 400 -> "INVALID_REQUEST";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "INVALID_REQUEST";
        };
        String message = switch (status.value()) {
            case 400 -> "Requisição inválida. Verifique os campos obrigatórios.";
            case 404 -> "Recurso não encontrado.";
            case 405 -> "Método HTTP não permitido para este recurso.";
            case 406 -> "Formato de resposta não suportado. Utilize application/json.";
            case 415 -> "Formato do corpo não suportado. Utilize application/json.";
            default -> "Não foi possível processar a requisição.";
        };
        if (status.is5xxServerError()) log.error("Falha interna no processamento HTTP.", exception);
        return buildError(status, code, message, request, List.of(), headers);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception exception, WebRequest request) {
        log.error("Falha inesperada ao processar requisição.", exception);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Erro interno ao processar a requisição.", request);
    }
}