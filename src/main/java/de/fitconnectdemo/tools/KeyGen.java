package de.fitconnectdemo.tools;

import de.fitconnectdemo.Env;
import dev.fitko.fitconnect.sdk.api.crypto.JWKPair;
import dev.fitko.fitconnect.sdk.util.TestKeyBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the Meldeamt's key pairs for the TEST environment using the SDK's own
 * {@link TestKeyBuilder} (the official tooling for exactly this: TEST-only keys, not V-PKI
 * backed), and writes them under the same file names the official FIT-Connect tooling uses:
 *
 * <ul>
 *   <li>{@code keys/publicKey_encryption.jwk.json} and {@code keys/publicKey_signature_verification.jwk.json}
 *       — upload these two to the Zustellpunkt in the Self-Service-Portal (section "Sicherheit").
 *       They carry what the portal validates: RSA keys, {@code alg} RSA-OAEP-256 / PS512,
 *       {@code key_ops} [wrapKey] / [verify], and a self-signed certificate chain (x5c).
 *   <li>{@code keys/privateKey_decryption.jwk.json} and {@code keys/privateKey_signing.jwk.json}
 *       — stay local, never leave this machine; the {@code receive} command decrypts and signs with them
 * </ul>
 *
 * <p>STAGE/PROD require real Verwaltungs-PKI certificates instead.
 */
public final class KeyGen {

    private KeyGen() {
    }

    public static void generate(boolean force) {
        List<KeyFile> files = List.of(
                new KeyFile("keys/privateKey_decryption.jwk.json", "private decryption key"),
                new KeyFile("keys/publicKey_encryption.jwk.json", "PUBLIC encryption key (upload to portal)"),
                new KeyFile("keys/privateKey_signing.jwk.json", "private signing key"),
                new KeyFile("keys/publicKey_signature_verification.jwk.json", "PUBLIC signature key (upload to portal)"));

        List<Path> existing = files.stream().map(KeyFile::path).filter(Files::exists).toList();
        if (!existing.isEmpty() && !force) {
            throw new IllegalStateException("Refusing to overwrite existing key file(s) " + existing
                    + " — delete them first, or pass --force (old submissions encrypted with the old key become unreadable).");
        }

        JWKPair encryption = TestKeyBuilder.generateEncryptionKeyPair();
        JWKPair signing = TestKeyBuilder.generateSignatureKeyPair();

        write(files.get(0), encryption.privateKey().toJSONString());
        write(files.get(1), encryption.publicKey().toJSONString());
        write(files.get(2), signing.privateKey().toJSONString());
        write(files.get(3), signing.publicKey().toJSONString());

        System.out.println("Generated 4 key files under keys/ (public keys with self-signed certificate chain):");
        for (KeyFile file : files) {
            System.out.println("  " + file.path() + "  — " + file.description());
        }
        System.out.println();
        System.out.println("Next steps:");
        System.out.println("  1. Upload the two PUBLIC files in the Self-Service-Portal, Meldeamt Zustellpunkt, section 'Sicherheit'.");
        System.out.println("  2. Keep the two PRIVATE files where they are — .env already points at them.");
        System.out.println("  3. Never commit keys/ (it is git-ignored).");
    }

    private static void write(KeyFile file, String content) {
        try {
            Files.createDirectories(file.path().getParent());
            Files.writeString(file.path(), content);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file.path(), e);
        }
    }

    private record KeyFile(Path path, String description) {
        private KeyFile(String path, String description) {
            this(Path.of(path), description);
        }
    }
}
