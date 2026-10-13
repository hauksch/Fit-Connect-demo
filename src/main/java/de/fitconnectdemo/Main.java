package de.fitconnectdemo;

import de.fitconnectdemo.receiver.Receiver;
import de.fitconnectdemo.sender.Sender;
import java.util.Arrays;
import java.util.UUID;

/**
 * Entry point for the demo tools. Usage:
 *
 * <pre>
 *   check              connectivity + credentials smoke test against the TEST environment
 *   send               send one fake-xMeld Meldeauskunft-Anfrage to the simulated Meldeamt
 *   events &lt;caseId&gt;    print the signed event log of a case
 *   replies            pick up, decrypt and accept the Meldeamt's replies (sender side)
 *   receive [--loop] [--no-reply]
 *                      Meldeamt side: poll, decrypt, accept, answer each submission
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        String command = args.length == 0 ? "help" : args[0];
        try {
            switch (command) {
                case "check" -> check();
                case "send" -> Sender.send();
                case "events" -> Sender.events(caseIdFrom(args));
                case "replies" -> Sender.replies();
                case "receive" -> Receiver.receive(hasFlag(args, "--loop"), hasFlag(args, "--no-reply"));
                case "genkeys" -> de.fitconnectdemo.tools.KeyGen.generate(hasFlag(args, "--force"));
                default -> printHelp();
            }
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Exercises directory lookup and OAuth against the TEST environment without sending anything. */
    private static void check() {
        UUID receiverDestinationId = Env.requireUuid("RECEIVER_DESTINATION_ID");
        var encryptionKey = SdkFactory.forSender().directory().activeEncryptionKeyOf(receiverDestinationId);
        System.out.println("TEST environment reachable.");
        System.out.println("Meldeamt destination " + receiverDestinationId);
        System.out.println("Active encryption key: " + encryptionKey.keyId());
        long cases = SdkFactory.forSender()
                .onlineService(Env.requireUuid("SENDER_DESTINATION_ID"),
                        dev.fitko.fitconnect.sdk.api.crypto.ReplyKeys.none())
                .cases()
                .list()
                .size();
        System.out.println("Sender credentials work — " + cases + " case(s) visible so far.");
    }

    private static UUID caseIdFrom(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: events <caseId>");
        }
        return UUID.fromString(args[1]);
    }

    private static boolean hasFlag(String[] args, String flag) {
        return Arrays.asList(args).contains(flag);
    }

    private static void printHelp() {
        System.out.println("""
                FIT-Connect demo — communication layer for Meldeamt exchanges (TEST environment, fake xMeld fachdaten)

                Usage: ./gradlew run --args="<command> [options]"
                  check                    connectivity + credentials smoke test
                  send                     send a fake-xMeld Meldeauskunft-Anfrage
                  events <caseId>          print a case's signed event log
                  replies                  pick up and accept the Meldeamt's replies (sender side)
                  receive [--loop]         Meldeamt side: poll, decrypt, accept, answer
                            [--no-reply]   ...but without sending the Antwort
                  genkeys [--force]        generate the Meldeamt's key pairs under keys/
                """);
    }
}
