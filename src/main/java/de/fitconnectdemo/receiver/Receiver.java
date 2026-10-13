package de.fitconnectdemo.receiver;

import com.nimbusds.jose.jwk.JWK;
import de.fitconnectdemo.Env;
import de.fitconnectdemo.SdkFactory;
import de.fitconnectdemo.fachdaten.FakeXMeld;
import dev.fitko.fitconnect.rest.model.reply.SentReply;
import dev.fitko.fitconnect.rest.model.submission.SubmissionForPickup;
import dev.fitko.fitconnect.sdk.FitConnectSdk;
import dev.fitko.fitconnect.sdk.api.OutgoingReply;
import dev.fitko.fitconnect.sdk.api.ReceivedSubmission;
import dev.fitko.fitconnect.sdk.api.SubmissionData;
import dev.fitko.fitconnect.sdk.clients.Organisation;
import dev.fitko.fitconnect.sdk.config.credentials.DestinationKeys;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.List;

/**
 * The receiving side (administrative system, type A) simulating an Einwohnermeldeamt: polls for
 * incoming submissions, decrypts and validates them, accepts them, and answers each one with a
 * fake-xMeld Meldeauskunft-Antwort through the case's reply channel.
 */
public final class Receiver {

    private Receiver() {
    }

    /**
     * @param loop    keep polling forever instead of one pass
     * @param noReply receive and accept only, don't send the Antwort
     */
    public static void receive(boolean loop, boolean noReply) throws InterruptedException {
        FitConnectSdk sdk = SdkFactory.forReceiver();
        DestinationKeys keys = new DestinationKeys(
                loadJwk(Env.path("RECEIVER_DECRYPTION_JWK_FILE", "keys/privateKey_decryption.jwk.json")),
                loadJwk(Env.path("RECEIVER_SIGNATURE_JWK_FILE", "keys/privateKey_signing.jwk.json")));
        Organisation meldeamt = sdk.organisation(Env.requireUuid("RECEIVER_DESTINATION_ID"), keys);

        int pollIntervalSeconds = Env.intValue("POLL_INTERVAL_SECONDS", 5);
        do {
            List<SubmissionForPickup> waiting = meldeamt.awaitingSubmissions();
            if (waiting.isEmpty()) {
                if (loop) {
                    System.out.println("Inbox empty — next poll in " + pollIntervalSeconds + "s");
                    Thread.sleep(pollIntervalSeconds * 1000L);
                }
            } else {
                for (SubmissionForPickup pickup : waiting) {
                    process(meldeamt, pickup, noReply);
                }
            }
        } while (loop);
    }

    private static void process(Organisation meldeamt, SubmissionForPickup pickup, boolean noReply) {
        try {
            ReceivedSubmission received = meldeamt.receive(pickup);
            System.out.println("Received submission " + received.submissionId()
                    + " on case " + received.caseId()
                    + " from destination " + received.senderDestinationId());
            System.out.println("Fachdaten (" + received.getDataMimeType()
                    + ", schema " + received.getDataSchemaUri() + "):");
            System.out.println(received.getDataAsString());

            if (!received.acceptable()) {
                System.out.println("Submission NOT acceptable:\n" + received.report().describe());
                meldeamt.reject(received, received.report().asProblems());
                return;
            }
            meldeamt.accept(received);
            System.out.println("Submission accepted.");

            if (!noReply) {
                sendAntwort(meldeamt, received);
            }
        } catch (Exception e) {
            System.err.println("Processing submission " + pickup.submissionId() + " failed: " + e.getMessage());
        }
    }

    private static void sendAntwort(Organisation meldeamt, ReceivedSubmission received) {
        SentReply reply = meldeamt.send(OutgoingReply.answering(received)
                .setData(SubmissionData.xml(
                        FakeXMeld.antwort(received.caseId(), "Musterweg 1, 12345 Musterstadt"),
                        URI.create(Env.require("REPLY_SCHEMA_URI"))))
                .build());
        System.out.println("Meldeauskunft-Antwort sent: replyId=" + reply.replyId());
        System.out.println("The sender picks it up with:  ./gradlew run --args=\"replies\"");
    }

    private static JWK loadJwk(Path file) {
        if (!Files.exists(file)) {
            throw new IllegalStateException("JWK file missing: " + file
                    + " — export the Meldeamt destination keys in the Self-Service-Portal and reference them in .env");
        }
        try {
            return JWK.parse(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        } catch (ParseException e) {
            throw new IllegalStateException(file + " does not contain a valid JWK", e);
        }
    }
}
