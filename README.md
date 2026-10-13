# fitconnect-testing

Communication layer for exchanging messages with German **Einwohnermeldeämter** over
[FIT-Connect](https://docs.fitko.de/fit-connect/), built and tested against the **TEST environment**
(`*.fit-connect.fitko.dev`). The Fachdatenebene (xMeld) is **faked** — payloads are minimal,
well-formed XML in an xMeld-style namespace. Everything else is real: OAuth2, end-to-end
encryption (JWE), certificate-backed keys (`x5c`), signed event logs (SET), submission,
acceptance, and replies.

Built on the official Java SDK `dev.fitko.fitconnect:sdk-client` **4.0.0-rc.2** (Java 21).

## How the demo is wired

Both ends of the exchange live in this one project and in your own TEST-environment account
(free: the [Self-Service-Portal](https://portal.auth-testing.fit-connect.fitko.dev) lets you
create clients, Zustellpunkte, and self-signed test keys yourself):

```
+--------------------------+         +----------------------------+
| Sender  ("das Portal")   |         | Receiver ("das Meldeamt")  |
| Typ C  OnlineService     |         | Typ A  Organisation        |
| SENDER_CLIENT_*          |  FIT-   | RECEIVER_CLIENT_*          |
| SENDER_DESTINATION_ID    |  Connect| RECEIVER_DESTINATION_ID    |
|                          |  Zustel-| + decryption JWK (fachdaten)|
| send()  ---------------> | dienst  |    + signature JWK (events) |
|                          |         |  receive() -> accept()     |
| replies() <--------------+---------+-- send(OutgoingReply)      |
+--------------------------+         +----------------------------+
```

- `send` opens a case at the Meldeamt's destination with a fake **Meldeauskunft-Anfrage** (xMeld XML)
  and a **FIT-Connect reply channel** (per-case RSA key pair; the private key is stored under
  `var/reply-keys/<caseId>.json`).
- `receive` (Meldeamt side) polls the inbox, decrypts fachdaten, verifies the signed event chain,
  **accepts** the submission, and answers with a fake **Meldeauskunft-Antwort** through the case's
  reply channel.
- `replies` (sender side) picks up, decrypts and accepts that answer.
- `events <caseId>` prints the case's signed event log (`SUBMITTED → NOTIFIED → ACCEPTED …`).

## One-time onboarding (in the TEST Self-Service-Portal)

1. **Meldeamt destination** (Zustellpunkt of a Verwaltungssystem, type A):
   - register the service: `SERVICE_IDENTIFIER` from `.env` (default
     `urn:de:fim:leika:leistung:99400048079000`) and the submission schema `DATA_SCHEMA_URI`
     (default `https://demo.example.dev/schemas/xmeld/meldeauskunft-anfrage/1.0`, format **XML**).
     The values must match `.env` exactly, or sending is rejected.
   - enable the **FIT-Connect reply channel** for the destination (needed for the Antwort).
   - run `./gradlew run --args="genkeys"` — it creates four JWK files under `keys/` (same names the
     official FIT-Connect tooling uses). Upload the two `keys/publicKey_*.jwk.json` files in the
     Zustellpunkt's **Sicherheit** section; the two `privateKey_*.jwk.json` files stay local and
     are what `.env` points at.
   - note the destination id → `RECEIVER_DESTINATION_ID`; set the destination **active**.
2. **API client for the Meldeamt** (Verwaltungssystem client): note `RECEIVER_CLIENT_ID` /
   `RECEIVER_CLIENT_SECRET`, and tick the destination under **Erlaubte Zustellpunkte**.
3. **Online service** (type C) with an API client: note `SENDER_CLIENT_ID`, `SENDER_CLIENT_SECRET`,
   and its own destination id → `SENDER_DESTINATION_ID` (the SDK verifies this destination exists
   and is of type C when the sender client is built).
4. `cp .env.example .env` and fill in everything. **Never commit `.env`** (it is git-ignored);
   the client secret is shown exactly once when the portal creates it — store it somewhere safe.

## Running

Requirements: any recent JDK on `PATH`/`JAVA_HOME` to launch Gradle (the build auto-provisions the
JDK 21 toolchain for compilation). Then:

```bash
./gradlew run --args="check"              # smoke test: reachability + credentials + destination key
./gradlew run --args="send"               # send one Anfrage → prints caseId/submissionId
./gradlew run --args="receive --loop"     # Meldeamt side: poll, accept, answer
./gradlew run --args="events <caseId>"    # signed event log of a case
./gradlew run --args="replies"            # sender side: pick up the Antwort
./gradlew test                            # unit tests
```

A full round trip:

1. Terminal 1: `./gradlew run --args="receive --loop"`
2. Terminal 2: `./gradlew run --args="send"` → copy the printed `caseId`
3. Terminal 2: `./gradlew run --args="events <caseId>"` — watch `SUBMITTED → … → ACCEPTED`
4. Terminal 2: `./gradlew run --args="replies"` — the Meldeauskunft-Antwort arrives, is decrypted
   with the stored per-case key and accepted.

## Project layout

```
src/main/java/de/fitconnectdemo/
├── Main.java                  # command dispatch (check/send/events/replies/receive/genkeys)
├── Env.java                   # .env + environment configuration
├── SdkFactory.java            # one SDK instance per role, aimed at TEST
├── ReplyKeyStore.java         # per-case private reply-channel keys (var/reply-keys/)
├── fachdaten/FakeXMeld.java   # fake xMeld Anfrage/Antwort XML  ← replace with real FIM later
├── sender/Sender.java         # type C: send, events, replies
├── receiver/Receiver.java     # type A: poll, decrypt, accept, answer
└── tools/KeyGen.java          # TEST key pairs via the SDK's own TestKeyBuilder
```

## Deliberate demo simplifications

- **Fachdaten are fake.** `FakeXMeld` produces xMeld-lookalike XML; the schema URIs exist nowhere.
  That's why `SdkFactory` disables data-schema validation (`validateData=false`) — re-enable it (or
  feed the schema via `ValidationConfig.customSchemas`) when real schemas arrive.
- **Reply keys on disk** (`var/reply-keys/`, git-ignored). A real system keeps them in a vault/HSM.
- **Polling only.** FIT-Connect also supports HTTP callbacks for the receiving side — a public
  endpoint is the natural next step for a real Meldeamt integration.
- **One process per role.** Two OAuth clients, one JVM invocation each; no concurrency locking
  between multiple receiver instances (the SDK docs warn about double processing).
- **TEST-only keys.** `genkeys` produces self-signed certificate-backed JWKs, which the TEST
  environment explicitly allows. STAGE/PROD require real Verwaltungs-PKI certificates — the code
  stays the same, only the key files change.
- TEST has no availability guarantees, and the SDK is a release candidate — the exact version is
  pinned in `build.gradle`.

## Security notes

- `.env`, `keys/`, and `var/` hold credentials and private key material and are git-ignored.
  Check all three before publishing any fork or copy.
- Everything in this repo was built against the TEST environment only; nothing here carries
  production credentials.

## License

[MIT](LICENSE)

