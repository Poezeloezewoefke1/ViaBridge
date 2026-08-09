package dev.viabridge.platform;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;
import dev.viabridge.ViaBridge;
import dev.viabridge.config.BridgeSettings;
import dev.viabridge.net.ServerVersionCache;
import io.netty.channel.Channel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Decides which protocol version each outgoing connection should present itself as.
 *
 * <p>Via calls {@link #getClosestServerProtocol} while the handshake is being written, and
 * rewrites the protocol field of that handshake to whatever comes back. Returning 774 here
 * is what makes a 1.21.4 client introduce itself to a 1.21.11 server as a 1.21.11 client;
 * ViaBackwards then translates the traffic in both directions.
 */
public final class BridgeVersionProvider implements VersionProvider {

    /** What we actually are: 1.21.4. */
    @Override
    public ProtocolVersion getClientProtocol(final UserConnection connection) {
        return ProtocolVersion.getProtocol(BridgeSettings.NATIVE_PROTOCOL);
    }

    @Override
    public ProtocolVersion getClosestServerProtocol(final UserConnection connection) {
        if (!connection.isClientSide()) {
            // Inbound connection (an open-to-LAN world). We are the server there, and we
            // are genuinely 1.21.4 — no translation.
            return ProtocolVersion.getProtocol(BridgeSettings.NATIVE_PROTOCOL);
        }

        final BridgeSettings settings = ViaBridge.settings();
        if (settings.autoDetect()) {
            final ProtocolVersion detected =
                ServerVersionCache.resolve(remoteAddress(connection), settings.detectTimeoutMillis());
            if (detected != null) {
                return detected;
            }
        }
        return settings.targetVersion();
    }

    private static InetSocketAddress remoteAddress(final UserConnection connection) {
        final Channel channel = connection.getChannel();
        if (channel == null) {
            return null;
        }
        final SocketAddress address = channel.remoteAddress();
        return address instanceof InetSocketAddress inet ? inet : null;
    }
}
