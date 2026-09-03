package br.com.sicredi.desafiovotacao.adapter.in.web.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

public class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Janela(long inicio, int requisicoes) {}
    private final ConcurrentHashMap<String, Janela> janelas = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int limite;
    private final SecurityErrorWriter errors;
    public AuthRateLimitFilter(Clock clock, int limite, SecurityErrorWriter errors) {
        this.clock = clock; this.limite = limite; this.errors = errors;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest req) {
        return !req.getRequestURI().startsWith("/api/v1/auth/") || !req.getMethod().equals("POST");
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        long agora = clock.millis();
        if (janelas.size() >= 10000) janelas.entrySet().removeIf(entry -> agora - entry.getValue().inicio() >= 60000);
        String origem = req.getRemoteAddr();
        if (janelas.size() >= 10000 && !janelas.containsKey(origem)) { negar(req, res); return; }
        Janela janela = janelas.compute(origem, (key, old) -> old == null || agora - old.inicio() >= 60000
                ? new Janela(agora, 1) : new Janela(old.inicio(), old.requisicoes() + 1));
        if (janela.requisicoes() > limite) { negar(req, res); return; }
        chain.doFilter(req, res);
    }
    private void negar(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setHeader("Retry-After", "60");
        errors.write(req, res, 429, "LIMITE_TENTATIVAS", "Muitas tentativas. Aguarde um minuto e tente novamente.");
    }
}
