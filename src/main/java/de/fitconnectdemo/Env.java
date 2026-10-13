package de.fitconnectdemo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration for the demo tools. Values are read from real environment variables first, then
 * from a {@code .env} file in the working directory (see {@code .env.example}).
 */
public final class Env {

    private static final Map<String, String> FILE_VALUES = loadFile(Path.of(".env"));

    private Env() {
    }

    public static String get(String key) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return FILE_VALUES.get(key);
    }

    public static String getOrDefault(String key, String defaultValue) {
        String value = get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public static String require(String key) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing configuration value: " + key + " — set it in .env (see .env.example)");
        }
        return value;
    }

    public static UUID requireUuid(String key) {
        try {
            return UUID.fromString(require(key));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(key + " must be a UUID but was: " + get(key), e);
        }
    }

    public static Path path(String key, String defaultValue) {
        return Path.of(getOrDefault(key, defaultValue));
    }

    public static int intValue(String key, int defaultValue) {
        String value = get(key);
        return value == null || value.isBlank() ? defaultValue : Integer.parseInt(value);
    }

    private static Map<String, String> loadFile(Path file) {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(file)) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
        return values;
    }
}
