package br.com.sicredi.desafiovotacao.config;

import com.nimbusds.jose.jwk.RSAKey;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPairGenerator;
import java.security.interfaces.*;
import java.util.UUID;

final class JwtKeyStore {
    private JwtKeyStore() {}
    static RSAKey carregar(Path caminho) throws Exception {
        Path path = caminho.toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            var pair = generator.generateKeyPair();
            var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(UUID.randomUUID().toString()).build();
            Path temporario = Files.createTempFile(path.getParent(), ".jwt-", ".tmp");
            try {
                if (Files.getFileStore(temporario).supportsFileAttributeView("posix"))
                    Files.setPosixFilePermissions(temporario, PosixFilePermissions.fromString("rw-------"));
                Files.writeString(temporario, key.toJSONString());
                try { Files.move(temporario, path); } catch (FileAlreadyExistsException anotherInstance) { /* Reutiliza a chave persistida. */ }
            } finally { Files.deleteIfExists(temporario); }
        }
        RSAKey key = RSAKey.parse(Files.readString(path));
        if (!key.isPrivate() || key.toRSAPublicKey().getModulus().bitLength() < 2048) throw new IllegalStateException("Chave JWT inválida.");
        return key;
    }
}
