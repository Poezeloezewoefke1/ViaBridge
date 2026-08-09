package dev.viabridge.platform;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.platform.ViaPlatformLoader;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;

/**
 * Registers the providers Via asks the host for. The only one that matters client-side is
 * the {@link VersionProvider}: it is what decides, per connection, which server version we
 * are pretending to be.
 */
public final class BridgeLoader implements ViaPlatformLoader {

    @Override
    public void load() {
        Via.getManager().getProviders().use(VersionProvider.class, new BridgeVersionProvider());
    }

    @Override
    public void unload() {
        // Nothing retained.
    }
}
