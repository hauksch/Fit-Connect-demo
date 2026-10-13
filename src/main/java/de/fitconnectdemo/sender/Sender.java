package de.fitconnectdemo.sender;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import de.fitconnectdemo.Env;
import de.fitconnectdemo.ReplyKeyStore;
import de.fitconnectdemo.SdkFactory;
import de.fitconnectdemo.fachdaten.FakeXMeld;
import dev.fitko.fitconnect.rest.model.metadata.v2.DataSetSchema;
import dev.fitko.fitconnect.rest.model.reply.ReplyForPickup;
import dev.fitko.fitconnect.rest.model.submission.SentSubmission;
import dev.fitko.fitconnect.sdk.FitConnectSdk;
import dev.fitko.fitconnect.sdk.api.Addressing;
import dev.fitko.fitconnect.sdk.api.OutgoingSubmission;
import dev.fitko.fitconnect.sdk.api.Participant;
import dev.fitko.fitconnect.sdk.api.ReceivedReply;
import dev.fitko.fitconnect.sdk.api.ReplyChannels;
import dev.fitko.fitconnect.sdk.api.SubmissionData;
import dev.fitko.fitconnect.sdk.api.crypto.ReplyKeys;
import dev.fitko.fitconnect.sdk.api.event.CaseEvent;
import dev.fitko.fitconnect.sdk.clients.OnlineService;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * The sending side (online service, type C): sends a fake-xMeld Meldeauskunft-Anfrage to the
 * simulated Meldeamt, tracks case events, and picks up the Meldeamt's replies.
 */
public final class Sender {

    private Sender() {
    }

    /** Sends one fake-xMeld Anfrage with a FIT-Connect reply channel, so the Amt can answer. */
    public static void send() throws JOSEException {
        UUID senderDestinationId = Env.requireUuid("SENDER_DESTINATION_ID");
        URI dataSchema = URI.create(Env.require("DATA_SCHEMA_URI"));
        URI replySchema = URI.create(Env.require("REPLY_SCHEMA_URI"));
        ReplyKeyStore replyKeyStore = new ReplyKeyStore(Path.of("var", "reply-keys"));

        FitConnectSdk sdk = SdkFactory.forSender();
        OnlineService portal = sdk.onlineService(senderDestinationId, replyKeyStore.asReplyKeys());

        // Per-case key pair: the public half travels in the submission's reply channel, the private
        // half stays here so only we can open the Meldeamt's reply to this case.
        RSAKey replyKeyPair = new RSAKeyGenerator(2048)
                .keyUse(KeyUse.ENCRYPTION)
                .keyID(UUID.randomUUID().toString())
                .generate();

        String serviceIdentifier = Env.require("SERVICE_IDENTIFIER");
        String serviceName = Env.require("SERVICE_NAME");
        String region = Env.get("SERVICE_REGION");
        Participant meldeamt = Participant.of(
                Env.requireUuid("RECEIVER_DESTINATION_ID"),
                region == null || region.isBlank()
                        ? Addressing.toService(serviceIdentifier, serviceName)
                        : Addressing.toService(serviceIdentifier, serviceName, region));

        UUID anfrageId = UUID.randomUUID();
        OutgoingSubmission submission = OutgoingSubmission.to(meldeamt)
                .setData(SubmissionData.xml(
                        FakeXMeld.anfrage(anfrageId, "Erika", "Mustermann", "1970-01-01"), dataSchema))
                .setReplyChannel(ReplyChannels.fitConnect(
                        replyKeyPair, List.of(new DataSetSchema(replySchema.toString(), "application/xml"))))
                .build();

        SentSubmission sent = portal.send(submission);
        replyKeyStore.store(sent.caseId(), replyKeyPair);

        System.out.println("Meldeauskunft-Anfrage " + anfrageId + " sent.");
        System.out.println("  caseId       = " + sent.caseId());
        System.out.println("  submissionId = " + sent.submissionId());
        System.out.println("Track it:  ./gradlew run --args=\"events " + sent.caseId() + "\"");
        System.out.println("Pick up the answer later:  ./gradlew run --args=\"replies\"");
    }

    /** Prints the signed event log (SET chain) of a case, oldest first. */
    public static void events(UUID caseId) {
        FitConnectSdk sdk = SdkFactory.forSender();
        OnlineService portal = sdk.onlineService(senderDestinationId(), ReplyKeys.none());

        List<CaseEvent> events = portal.cases().events(caseId);
        if (events.isEmpty()) {
            System.out.println("No events on case " + caseId);
            return;
        }
        System.out.println("Event log of case " + caseId + ":");
        for (CaseEvent event : events) {
            System.out.printf("  %s  %-12s issuer=%s submission=%s%n",
                    event.issueTime(), event.state(), event.issuer(), event.submissionId());
        }
    }

    /** Lists, decrypts and accepts the Meldeamt's replies to our cases. */
    public static void replies() {
        FitConnectSdk sdk = SdkFactory.forSender();
        OnlineService portal = sdk.onlineService(senderDestinationId(), new ReplyKeyStore(Path.of("var", "reply-keys")).asReplyKeys());

        List<ReplyForPickup> waiting = portal.awaitingReplies();
        if (waiting.isEmpty()) {
            System.out.println("No replies waiting. (Send one with `receive` on the Meldeamt side.)");
            return;
        }
        for (ReplyForPickup pickup : waiting) {
            ReceivedReply reply = portal.receive(pickup);
            System.out.println("Reply " + reply.id() + " on case " + reply.caseId()
                    + " from destination " + reply.senderDestinationId());
            System.out.println("Fachdaten (" + reply.getDataMimeType() + ", schema " + reply.getDataSchemaUri() + "):");
            System.out.println(reply.getDataAsString());
            portal.accept(reply);
            System.out.println("Reply accepted.");
        }
    }

    private static UUID senderDestinationId() {
        return Env.requireUuid("SENDER_DESTINATION_ID");
    }
}
