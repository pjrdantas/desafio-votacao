package br.com.sicredi.desafiovotacao.adapter.in.web.exception;

import br.com.sicredi.desafiovotacao.adapter.in.web.CorrelationIdFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public abstract class BaseApiExceptionHandler extends ResponseEntityExceptionHandler {
    private final Clock clock;

    protected BaseApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    protected ResponseEntity<Object> buildError(HttpStatusCode status, String error, String message,
            WebRequest request, List<ApiFieldError> fields, HttpHeaders originalHeaders) {
        ServletWebRequest web = (ServletWebRequest) request;
        Object attribute = web.getRequest().getAttribute(CorrelationIdFilter.ATTRIBUTE);
        String correlationId = attribute instanceof String value ? value : UUID.randomUUID().toString();
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(originalHeaders);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(CorrelationIdFilter.HEADER, correlationId);
        ApiErrorResponse response = new ApiErrorResponse(clock.instant(), status.value(), error, message,
                web.getRequest().getRequestURI(), fields, correlationId);
        return new ResponseEntity<>(response, headers, status);
    }

    protected ResponseEntity<Object> buildError(HttpStatusCode status, String error, String message,
            WebRequest request) {
        return buildError(status, error, message, request, List.of(), HttpHeaders.EMPTY);
    }
}