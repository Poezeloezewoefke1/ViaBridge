package dev.viabridge.platform;

import com.viaversion.viaversion.platform.UserConnectionViaVersionPlatform;
import dev.viabridge.ViaBridge;
import dev.viabridge.util.JLoggerToLog4j;
import java.io.File;
import java.util.logging.Logger;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
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
        return FabricLoader.getInstance().getModContainer("minecraft")
            .map(ModContainer::getMetadata)
            .map(ModMetadata::getVersion)
            .map(Object::toString)
            .orElse("1.21.4");
    }

    @Override
    public String getPluginVersion() {
        return FabricLoader.getInstance().getModContainer(ViaBridge.MOD_ID)
            .map(ModContainer::getMetadata)
            .map(ModMetadata::getVersion)
            .map(Object::toString)
            .orElse("unknown");
    }
}
