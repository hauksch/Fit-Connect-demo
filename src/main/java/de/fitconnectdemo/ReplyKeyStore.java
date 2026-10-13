package de.fitconnectdemo;

import com.nimbusds.jose.jwk.JWK;
import dev.fitko.fitconnect.sdk.api.crypto.ReplyKeys;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists the private reply-channel key the sender generates per case, one JWK file per case under
 * {@code var/reply-keys/}. The submission's reply channel carries the matching public key; without
 * the private half the sender could never open the Meldeamt's reply.
 *
 * <p>A real system would keep these in a vault; files are enough for the demo.
 */
public final class ReplyKeyStore {

    private final Path directory;

    public ReplyKeyStore(Path directory) {
        this.directory = directory;
    }

    public void store(UUID caseId, JWK privateKey) {
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(caseId + ".json"), privateKey.toJSONString());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store reply key for case " + caseId, e);
        }
    }

    public Optional<JWK> load(UUID caseId) {
        Path file = directory.resolve(caseId + ".json");
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(JWK.parse(Files.readString(file)));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read reply key for case " + caseId, e);
        } catch (ParseException e) {
            throw new IllegalStateException("Reply key for case " + caseId + " is not a valid JWK", e);
        }
    }

    /** View of this store for the SDK's online-service client. */
    public ReplyKeys asReplyKeys() {
        return this::load;
    }
}
