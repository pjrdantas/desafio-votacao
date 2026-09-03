package br.com.sicredi.desafiovotacao.application.port.out;

import br.com.sicredi.desafiovotacao.domain.Usuario;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository {
    Usuario salvar(Usuario usuario);
    Optional<Usuario> buscarCpf(String cpf);
    Optional<Usuario> buscarId(UUID id);
    boolean bloqueado(UUID id, Instant agora);
    void registrarFalha(UUID id, Instant agora);
    void limparFalhas(UUID id);
}
