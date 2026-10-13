package de.fitconnectdemo;

import dev.fitko.fitconnect.rest.client.config.FitConnectEnvironment;
import dev.fitko.fitconnect.sdk.FitConnectSdk;
import dev.fitko.fitconnect.sdk.config.settings.SdkSettings;
import dev.fitko.fitconnect.sdk.config.settings.ValidationConfig;

/**
 * Builds one {@link FitConnectSdk} instance per role, both aimed at the TEST environment.
 * Each role uses its own OAuth client from the Self-Service-Portal.
 */
public final class SdkFactory {

    private SdkFactory() {
    }

    /** SDK for the sending side (online service, type C). */
    public static FitConnectSdk forSender() {
        return FitConnectSdk.fromConfigBuilder()
                .credentials(Env.require("SENDER_CLIENT_ID"), Env.require("SENDER_CLIENT_SECRET"))
                .environment(FitConnectEnvironment.TEST)
                .settings(demoSettings())
                .build();
    }

    /** SDK for the receiving side (administrative system, type A — the simulated Meldeamt). */
    public static FitConnectSdk forReceiver() {
        return FitConnectSdk.fromConfigBuilder()
                .credentials(Env.require("RECEIVER_CLIENT_ID"), Env.require("RECEIVER_CLIENT_SECRET"))
                .environment(FitConnectEnvironment.TEST)
                .settings(demoSettings())
                .build();
    }

    /**
     * Data validation is off because the demo sends fake xMeld fachdaten whose schema URI does not
     * exist anywhere — schema validation would fail against a schema nobody serves. Re-enable (or
     * supply a schema via {@code customSchemas}) once the real Fachdatenebene is in scope.
     */
    private static SdkSettings demoSettings() {
        return SdkSettings.builder()
                .validationConfig(ValidationConfig.builder().validateData(false).build())
                .build();
    }
}
