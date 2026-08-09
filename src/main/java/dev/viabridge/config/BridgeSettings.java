package dev.viabridge.config;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import dev.viabridge.ViaBridge;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;

/**
 * ViaBridge's own settings, kept separate from Via's {@code viaversion.yml} (which stays
 * where Via expects it and governs Via's internal behaviour).
 */
public final class BridgeSettings {

    /** Protocol version of the client this mod is built against — 1.21.4. */
    public static final int NATIVE_PROTOCOL = 769;

    /** Protocol version of 1.21.11, the version this mod is primarily aimed at. */
    public static final int DEFAULT_TARGET_PROTOCOL = 774;

    private final boolean enabled;
    private final ProtocolVersion targetVersion;
    private final boolean autoDetect;
    private final int detectTimeoutMillis;

    private BridgeSettings(final boolean enabled, final ProtocolVersion targetVersion,
                           final boolean autoDetect, final int detectTimeoutMillis) {
        this.enabled = enabled;
        this.targetVersion = targetVersion;
        this.autoDetect = autoDetect;
        this.detectTimeoutMillis = detectTimeoutMillis;
    }

    /** Master switch. When off, connections are left completely untouched. */
    public boolean enabled() {
        return enabled;
    }

    /** Version used when auto-detection is off, or when it fails or times out. */
    public ProtocolVersion targetVersion() {
        return targetVersion;
    }

    /** Whether to ping the server for its real version before connecting. */
    public boolean autoDetect() {
        return autoDetect;
    }

    /** How long a connection attempt will wait for that ping. */
    public int detectTimeoutMillis() {
        return detectTimeoutMillis;
    }

    public static BridgeSettings load(final Path file) {
        final Properties defaults = new Properties();
        defaults.setProperty("enabled", "true");
        defaults.setProperty("target-version", "1.21.11");
        defaults.setProperty("auto-detect", "true");
        defaults.setProperty("detect-timeout-ms", "3000");

        final Properties properties = new Properties(defaults);
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (final IOException e) {
                ViaBridge.LOGGER.log(Level.WARNING,
                    "Could not read " + file + ", falling back to defaults", e);
            }
        } else {
            write(file, defaults);
        }

        return new BridgeSettings(
            Boolean.parseBoolean(properties.getProperty("enabled", "true")),
            parseVersion(properties.getProperty("target-version", "1.21.11")),
            Boolean.parseBoolean(properties.getProperty("auto-detect", "true")),
            parseInt(properties.getProperty("detect-timeout-ms", "3000"), 3000));
    }

    private static void write(final Path file, final Properties defaults) {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                defaults.store(out, """
                    ViaBridge

                    target-version     server version assumed when auto-detect is off or fails.
                                       Accepts a name ("1.21.11") or a raw protocol number ("774").
                    auto-detect        ping the server for its real version before connecting.
                    detect-timeout-ms  how long connecting waits for that ping before falling
                                       back to target-version.""");
            }
        } catch (final IOException e) {
            ViaBridge.LOGGER.log(Level.WARNING, "Could not write default config to " + file, e);
        }
    }

    /** Accepts either a version name ("1.21.11") or a raw protocol number ("774"). */
    private static ProtocolVersion parseVersion(final String raw) {
        final String value = raw.trim();
        try {
            return ProtocolVersion.getProtocol(Integer.parseInt(value));
        } catch (final NumberFormatException ignored) {
            // Not a number — fall through to a name lookup.
        }

        final ProtocolVersion byName = ProtocolVersion.getClosest(value);
        if (byName != null) {
            return byName;
        }

        ViaBridge.LOGGER.warning("Unknown target-version '" + raw
            + "', falling back to 1.21.11 (" + DEFAULT_TARGET_PROTOCOL + ")");
        return ProtocolVersion.getProtocol(DEFAULT_TARGET_PROTOCOL);
    }

    private static int parseInt(final String raw, final int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }
}
