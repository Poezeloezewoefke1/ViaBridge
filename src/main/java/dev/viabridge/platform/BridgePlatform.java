package dev.viabridge.platform;

import com.viaversion.viaversion.platform.UserConnectionViaVersionPlatform;
import dev.viabridge.util.JLoggerToLog4j;
import java.io.File;
import java.util.Optional;
import java.util.logging.Logger;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.apache.logging.log4j.LogManager;

/**
 * Via's view of the host application.
 *
 * <p>{@link UserConnectionViaVersionPlatform} already supplies the API object, the config,
 * the data folder and — importantly — {@code isProxy() == true}, which is what tells Via
 * that this host is not pinned to one server version and may translate freely. Almost
 * everything else on {@code ViaPlatform} has a sensible default in Via 5.x, including task
 * scheduling, so this class stays small.
 */
public final class BridgePlatform extends UserConnectionViaVersionPlatform {

    public BridgePlatform(final File dataFolder) {
        super(dataFolder);
        //noinspection ResultOfMethodCallIgnored
        dataFolder.mkdirs();
    }

    @Override
    public Logger createLogger(final String name) {
        return new JLoggerToLog4j(LogManager.getLogger(name));
    }

    @Override
    public String getPlatformName() {
        return "ViaBridge";
    }

    /**
     * The version of the host game, which Via reports in dumps. Read from the loader
     * rather than from {@code SharedConstants} so that no Minecraft class — and therefore
     * no mapping — is involved.
     */
    @Override
    public String getPlatformVersion() {
        return modVersion("minecraft").orElse("1.21.4");
    }

    /**
     * The version of the <em>ViaVersion library</em> — deliberately not this mod's own
     * version.
     *
     * <p>Via addons treat this as the version of the Via core they are running against.
     * ViaBackwards compares it to the minimum core version it needs and disables itself if
     * it looks too old, so reporting the mod version here (1.0.0 &lt; 5.11.0) silently
     * switched off the entire translation layer, leaving the client to introduce itself as
     * 1.21.4 and get rejected as an outdated client.
     */
    @Override
    public String getPluginVersion() {
        return modVersion("viaversion").orElse("UNKNOWN");
    }

    private static Optional<String> modVersion(final String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
            .map(ModContainer::getMetadata)
            .map(ModMetadata::getVersion)
            .map(Version::getFriendlyString);
    }
}
