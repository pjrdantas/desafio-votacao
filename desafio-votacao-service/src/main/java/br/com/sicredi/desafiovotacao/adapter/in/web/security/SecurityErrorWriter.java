package br.com.sicredi.desafiovotacao.adapter.in.web.security;

import br.com.sicredi.desafiovotacao.adapter.in.web.CorrelationIdFilter;
import br.com.sicredi.desafiovotacao.adapter.in.web.exception.ApiErrorResponse;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.time.Clock;
import java.util.List;

@Component
public class SecurityErrorWriter {
    private final JsonMapper mapper;
    private final Clock clock;
    public SecurityErrorWriter(JsonMapper mapper, Clock clock) { this.mapper = mapper; this.clock = clock; }
    public void write(HttpServletRequest req, HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        if (status == 401) res.setHeader("WWW-Authenticate", "Bearer");
        mapper.writeValue(res.getOutputStream(), new ApiErrorResponse(clock.instant(), status, code, message,
            req.getRequestURI(), List.of(), (String) req.getAttribute(CorrelationIdFilter.ATTRIBUTE)));
    }
}
