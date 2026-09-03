package br.com.sicredi.desafiovotacao.adapter.out.postgres;

import br.com.sicredi.desafiovotacao.application.port.out.VotacaoRepository;
import br.com.sicredi.desafiovotacao.domain.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.sicredi.desafiovotacao.domain.exception.*;

@Repository
public class PostgresVotacaoRepository implements VotacaoRepository {
    private final JdbcClient jdbc;

    public PostgresVotacaoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Pauta salvarPauta(Pauta pauta) {
        jdbc.sql("INSERT INTO pauta(id, titulo, descricao, criada_em) VALUES (:id, :titulo, :descricao, :criada)")
                .param("id", pauta.id()).param("titulo", pauta.titulo())
                .param("descricao", pauta.descricao()).param("criada", Timestamp.from(pauta.criadaEm()))
                .update();
        return pauta;
    }

    @Override
    public Optional<Pauta> buscarPauta(UUID pautaId) {
        return jdbc.sql("SELECT * FROM pauta WHERE id = :id").param("id", pautaId)
                .query(this::mapearPauta).optional();
    }

    @Override
    public List<Pauta> listarPautas(int pagina, int tamanho) {
        return jdbc.sql("SELECT * FROM pauta ORDER BY criada_em DESC, id LIMIT :limite OFFSET :inicio")
                .param("limite", tamanho).param("inicio", (long) pagina * tamanho)
                .query(this::mapearPauta).list();
    }

    @Override
    public SessaoVotacao abrirSessao(UUID pautaId, int duracaoMinutos) {
        try {
            return jdbc.sql("""
                    WITH momento AS (SELECT clock_timestamp() AS agora)
                    INSERT INTO sessao_votacao(pauta_id, aberta_em, encerra_em)
                    SELECT :id, agora, agora + (:minutos * INTERVAL '1 minute')
                    FROM momento
                    RETURNING pauta_id, aberta_em, encerra_em
                    """).param("id", pautaId).param("minutos", duracaoMinutos)
                    .query(this::mapearSessao).single();
        } catch (DuplicateKeyException exception) {
            throw new SessaoJaExistenteException();
        }
    }

    @Override
    public void registrarVoto(Voto voto) {
        try {
            int inseridos = jdbc.sql("""
                    WITH momento AS (SELECT clock_timestamp() AS agora)
                    INSERT INTO voto(pauta_id, associado_id, escolha, registrado_em)
                    SELECT s.pauta_id, :associado, :escolha, m.agora
                    FROM sessao_votacao s CROSS JOIN momento m
                    WHERE s.pauta_id = :id AND m.agora >= s.aberta_em AND m.agora < s.encerra_em
                    """).param("id", voto.pautaId()).param("associado", voto.associadoId())
                    .param("escolha", voto.escolha().name()).update();
            if (inseridos == 0) {
                boolean possuiSessao = jdbc.sql("SELECT EXISTS(SELECT 1 FROM sessao_votacao WHERE pauta_id = :id)")
                        .param("id", voto.pautaId()).query(Boolean.class).single();
                throw possuiSessao ? new SessaoEncerradaException() : new SessaoNaoAbertaException();
            }
        } catch (DuplicateKeyException exception) {
            throw new VotoDuplicadoException();
        }
    }

    @Override
    public Optional<Resultado> buscarResultado(UUID pautaId) {
        return jdbc.sql("""
                SELECT p.id, s.aberta_em, s.encerra_em, statement_timestamp() AS apurado_em,
                    count(v.associado_id) FILTER (WHERE v.escolha = 'SIM') AS sim,
                    count(v.associado_id) FILTER (WHERE v.escolha = 'NAO') AS nao
                FROM pauta p
                LEFT JOIN sessao_votacao s ON s.pauta_id = p.id
                LEFT JOIN voto v ON v.pauta_id = p.id
                WHERE p.id = :id
                GROUP BY p.id, s.aberta_em, s.encerra_em
                """).param("id", pautaId).query((rs, row) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    Instant abertura = instanteOuNulo(rs, "aberta_em");
                    SessaoVotacao sessao = abertura == null ? null
                            : new SessaoVotacao(id, abertura, instanteOuNulo(rs, "encerra_em"));
                    return Resultado.apurar(id, sessao, rs.getLong("sim"), rs.getLong("nao"),
                            rs.getTimestamp("apurado_em").toInstant());
                }).optional();
    }

    private Pauta mapearPauta(ResultSet rs, int row) throws SQLException {
        return new Pauta(rs.getObject("id", UUID.class), rs.getString("titulo"),
                rs.getString("descricao"), rs.getTimestamp("criada_em").toInstant());
    }

    private SessaoVotacao mapearSessao(ResultSet rs, int row) throws SQLException {
        return new SessaoVotacao(rs.getObject("pauta_id", UUID.class),
                rs.getTimestamp("aberta_em").toInstant(), rs.getTimestamp("encerra_em").toInstant());
    }

    private Instant instanteOuNulo(ResultSet rs, String coluna) throws SQLException {
        Timestamp valor = rs.getTimestamp(coluna);
        return valor == null ? null : valor.toInstant();
    }
}