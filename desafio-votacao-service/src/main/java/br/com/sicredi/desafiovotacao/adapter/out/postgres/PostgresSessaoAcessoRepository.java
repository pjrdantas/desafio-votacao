package br.com.sicredi.desafiovotacao.adapter.out.postgres;

import br.com.sicredi.desafiovotacao.application.port.out.SessaoAcessoRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class PostgresSessaoAcessoRepository implements SessaoAcessoRepository {
    private final JdbcClient jdbc;
    public PostgresSessaoAcessoRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    @Override public void criar(UUID id, UUID usuario, String hash, Instant expiraEm) {
        jdbc.sql("INSERT INTO sessao_acesso(id,usuario_id,refresh_hash,expira_em) VALUES (:id,:usuario,:hash,:fim)")
            .param("id", id).param("usuario", usuario).param("hash", hash).param("fim", Timestamp.from(expiraEm)).update();
        jdbc.sql("DELETE FROM sessao_acesso WHERE expira_em < clock_timestamp() - INTERVAL '1 day'").update();
    }
    @Override public Optional<Renovacao> rotacionar(UUID id, String hash, String novoHash, Instant agora) {
        var atualizada = jdbc.sql("""
            UPDATE sessao_acesso SET refresh_anterior_hash=refresh_hash,refresh_hash=:novo
            WHERE id=:id AND refresh_hash=:hash AND revogada_em IS NULL AND expira_em > :agora
            RETURNING usuario_id,expira_em
            """).param("id", id).param("hash", hash).param("novo", novoHash).param("agora", Timestamp.from(agora))
            .query((rs, row) -> new Renovacao(rs.getObject("usuario_id", UUID.class), rs.getTimestamp("expira_em").toInstant())).optional();
        if (atualizada.isEmpty()) {
            // A reutilização do token imediatamente anterior revoga a sessão; valores desconhecidos não a revogam.
            jdbc.sql("UPDATE sessao_acesso SET revogada_em=:agora WHERE id=:id AND refresh_anterior_hash=:hash AND revogada_em IS NULL")
                .param("id", id).param("hash", hash).param("agora", Timestamp.from(agora)).update();
        }
        return atualizada;
    }
    @Override public boolean ativa(UUID id, UUID usuario, Instant agora) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM sessao_acesso WHERE id=:id AND usuario_id=:usuario AND revogada_em IS NULL AND expira_em>:agora)")
            .param("id", id).param("usuario", usuario).param("agora", Timestamp.from(agora)).query(Boolean.class).single();
    }
    @Override public void revogar(UUID id, String hash) {
        jdbc.sql("UPDATE sessao_acesso SET revogada_em=clock_timestamp() WHERE id=:id AND (refresh_hash=:hash OR refresh_anterior_hash=:hash)")
            .param("id", id).param("hash", hash).update();
    }
}
