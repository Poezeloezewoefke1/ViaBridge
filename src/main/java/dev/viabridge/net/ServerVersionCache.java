package dev.viabridge.net;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import dev.viabridge.ViaBridge;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

/**
 * Remembers what version each server reported, so a server list full of mixed versions
 * works without any per-server configuration.
 */
public final class ServerVersionCache {

    private static final long TTL_NANOS = TimeUnit.MINUTES.toNanos(5);

    private static final Map<InetSocketAddress, Entry> CACHE = new ConcurrentHashMap<>();

    private ServerVersionCache() {
    }

    /**
     * Returns the server's protocol version, pinging it if necessary.
     *
     * <p>Called while the handshake is being built, so it is bounded: if the ping has not
     * answered within {@code timeoutMillis} the caller falls back to the configured target
     * version, and the ping is left running to populate the cache for next time.
     *
     * @return the detected version, or {@code null} if detection did not finish in time
     */
    public static ProtocolVersion resolve(final InetSocketAddress address, final int timeoutMillis) {
        if (address == null) {
            return null;
        }

        final Entry entry = CACHE.compute(address, (key, existing) -> {
            if (existing != null && !existing.isExpired() && !existing.failed()) {
                return existing;
            }
            return new Entry(StatusPinger.ping(key));
        });

        try {
            return entry.future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            ViaBridge.LOGGER.fine("Version ping to " + address + " did not answer within "
                + timeoutMillis + "ms; using the configured target version");
            return null;
        } catch (final ExecutionException e) {
            ViaBridge.LOGGER.log(Level.FINE,
                "Version ping to " + address + " failed; using the configured target version",
                e.getCause());
            return null;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private record Entry(CompletableFuture<ProtocolVersion> future, long createdAt) {

        Entry(final CompletableFuture<ProtocolVersion> future) {
            this(future, System.nanoTime());
        }

        boolean isExpired() {
            return System.nanoTime() - createdAt > TTL_NANOS;
        }

        /** A failed ping should not be cached — the server may simply have been down. */
        boolean failed() {
            return future.isCompletedExceptionally();
        }
    }
}
