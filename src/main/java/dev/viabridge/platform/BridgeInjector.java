package dev.viabridge.platform;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.platform.NoopInjector;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import com.viaversion.viaversion.platform.ViaEncodeHandler;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import java.util.OptionalInt;

/**
 * Via normally injects itself into a server's channel initialiser. Client-side there is
 * no server socket to hook, so injection is a no-op and this class exists only to answer
 * "what server versions can this host speak?".
 *
 * <p>Reporting a range rather than a single version is what unlocks translation: Via uses
 * this set to decide which protocol paths are worth loading.
 */
public final class BridgeInjector extends NoopInjector {

    @Override
    public ObjectSortedSet<ProtocolVersion> getServerProtocolVersions() {
        final ObjectSortedSet<ProtocolVersion> versions = new ObjectLinkedOpenHashSet<>();
        versions.add(ProtocolVersion.v1_8);

        final OptionalInt highest = ProtocolVersion.getProtocols().stream()
            .mapToInt(ProtocolVersion::getOriginalVersion)
            .max();
        if (highest.isPresent()) {
            versions.add(ProtocolVersion.getProtocol(highest.getAsInt()));
        }
        return versions;
    }

    // These must agree with the names used when the handlers are spliced into the
    // pipeline, because Via looks the handlers back up by name when it reorders around
    // compression.

    @Override
    public String getEncoderName() {
        return ViaEncodeHandler.NAME;
    }

    @Override
    public String getDecoderName() {
        return ViaDecodeHandler.NAME;
    }
}
