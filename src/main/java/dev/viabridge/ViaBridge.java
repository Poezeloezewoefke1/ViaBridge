package dev.viabridge;

import com.viaversion.viaversion.ViaManagerImpl;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.configuration.AbstractViaConfig;
import dev.viabridge.config.BridgeSettings;
import dev.viabridge.platform.BridgeInjector;
import dev.viabridge.platform.BridgeLoader;
import dev.viabridge.platform.BridgePlatform;
import dev.viabridge.util.JLoggerToLog4j;
import java.nio.file.Path;
import java.util.logging.Logger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;

/**
 * Client-side protocol translation for Minecraft 1.21.4.
 *
 * <p>This mod lets a vanilla-protocol 1.21.4 client (protocol 769) join servers running
 * newer versions — 1.21.11 (protocol 774) in particular — with nothing installed on the
 * server side. It does that by standing up a {@code ViaVersion} manager inside the client
 * and splicing Via's packet translators into the Netty pipeline of every outgoing
 * connection.
 *
 * <p>The actual 774 -> 769 packet rewriting is done by <b>ViaBackwards</b>, which is
 * bundled inside this jar. ViaBackwards registers itself through the
 * {@code viafabric:via_api_initialized} entrypoint, which this class fires once the Via
 * manager is up. That entrypoint name is not ours to choose — it is the contract the Via
 * addons already ship against, which is also why this mod declares {@code provides:
 * viafabric} in its metadata.
 */
public final class ViaBridge implements ModInitializer {

    public static final String MOD_ID = "viabridge";

    /**
     * Via's API is built around {@link java.util.logging.Logger}, so everything it emits
     * has to be funnelled through a JUL facade onto Log4j.
     */
    public static final Logger LOGGER = new JLoggerToLog4j(LogManager.getLogger("ViaBridge"));

    private static BridgeSettings settings;

    public static BridgeSettings settings() {
        return settings;
    }

    @Override
    public void onInitialize() {
        final Path configDir = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
        settings = BridgeSettings.load(configDir.resolve("viabridge.properties"));

        final BridgePlatform platform = new BridgePlatform(configDir.toFile());

        Via.init(ViaManagerImpl.builder()
            .injector(new BridgeInjector())
            .loader(new BridgeLoader())
            .platform(platform)
            .build());

        // Via reads its own viaversion.yml out of our data folder; it is not loaded
        // implicitly by the manager.
        if (platform.getConf() instanceof AbstractViaConfig config) {
            config.reload();
        }

        final ViaManagerImpl manager = (ViaManagerImpl) Via.getManager();
        manager.init();

        // ViaBackwards' ViaFabricAddon hangs off this entrypoint. If it never runs, the
        // manager comes up with only the forwards protocols registered and every join
        // attempt against a newer server fails on an unknown protocol path.
        FabricLoader.getInstance()
            .getEntrypoints("viafabric:via_api_initialized", Runnable.class)
            .forEach(Runnable::run);

        // Signals to Via that the platform is fully set up and protocols may be loaded.
        manager.onServerLoaded();

        LOGGER.info("ViaBridge ready — native protocol "
            + BridgeSettings.NATIVE_PROTOCOL + " (1.21.4), default target "
            + settings.targetVersion() + ", auto-detect "
            + (settings.autoDetect() ? "on" : "off"));
    }
}
