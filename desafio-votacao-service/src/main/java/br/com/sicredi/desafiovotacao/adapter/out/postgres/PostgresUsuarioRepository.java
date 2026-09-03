package br.com.sicredi.desafiovotacao.adapter.out.postgres;

import br.com.sicredi.desafiovotacao.application.port.out.UsuarioRepository;
import br.com.sicredi.desafiovotacao.domain.*;
import br.com.sicredi.desafiovotacao.domain.exception.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Repository
public class PostgresUsuarioRepository implements UsuarioRepository {
    private final JdbcClient jdbc;
    public PostgresUsuarioRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    @Override public Usuario salvar(Usuario usuario) {
        try {
            jdbc.sql("INSERT INTO usuario(id,nome,cpf,senha_hash) VALUES (:id,:nome,:cpf,:senha)")
                .param("id", usuario.id()).param("nome", usuario.nome()).param("cpf", usuario.cpf().valor())
                .param("senha", usuario.senhaHash()).update();
            return usuario;
        } catch (DuplicateKeyException e) {
            throw new AcessoException(RegraNegocioException.Codigo.CPF_JA_CADASTRADO, "Não foi possível cadastrar este CPF. Se já possui conta, entre com sua senha.");
        }
    }
    @Override public Optional<Usuario> buscarCpf(String cpf) {
        return jdbc.sql("SELECT * FROM usuario WHERE cpf=:cpf").param("cpf", cpf).query(this::mapear).optional();
    }
    @Override public Optional<Usuario> buscarId(UUID id) {
        return jdbc.sql("SELECT * FROM usuario WHERE id=:id").param("id", id).query(this::mapear).optional();
    }
    @Override public boolean bloqueado(UUID id, Instant agora) {
        return jdbc.sql("SELECT COALESCE(bloqueado_ate > :agora,false) FROM usuario WHERE id=:id")
            .param("id", id).param("agora", Timestamp.from(agora)).query(Boolean.class).single();
    }
    @Override public void registrarFalha(UUID id, Instant agora) {
        jdbc.sql("""
            UPDATE usuario SET
                bloqueado_ate = CASE WHEN falhas_login >= 4 THEN CAST(:agora AS TIMESTAMPTZ) + INTERVAL '1 minute' ELSE NULL END,
                falhas_login = CASE WHEN falhas_login >= 4 THEN 0 ELSE falhas_login + 1 END
            WHERE id=:id
            """).param("id", id).param("agora", Timestamp.from(agora)).update();
    }
    @Override public void limparFalhas(UUID id) {
        jdbc.sql("UPDATE usuario SET falhas_login=0,bloqueado_ate=NULL WHERE id=:id").param("id", id).update();
    }
    private Usuario mapear(ResultSet rs, int row) throws SQLException {
        return new Usuario(rs.getObject("id", UUID.class), rs.getString("nome"), new Cpf(rs.getString("cpf")), rs.getString("senha_hash"));
    }
}
