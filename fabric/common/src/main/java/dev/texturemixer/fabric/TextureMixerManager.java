package dev.texturemixer.fabric;

import dev.texturemixer.core.TextureMixerConstants;
import dev.texturemixer.core.generate.DirectoryPackWriter;
import dev.texturemixer.core.generate.GenerationOptions;
import dev.texturemixer.core.generate.GenerationResult;
import dev.texturemixer.core.generate.PackGenerator;
import dev.texturemixer.core.generate.ZipPackWriter;
import dev.texturemixer.core.pack.PackProblem;
import dev.texturemixer.core.pack.PackScanner;
import dev.texturemixer.core.pack.PackSource;
import dev.texturemixer.core.pack.ScanResult;
import dev.texturemixer.core.profile.ProfileStore;
import dev.texturemixer.core.selection.Selections;
import dev.texturemixer.core.session.MixerSession;
import dev.texturemixer.core.texture.IndexCache;
import dev.texturemixer.core.texture.TextureIndex;
import dev.texturemixer.core.texture.TextureIndexer;
import dev.texturemixer.core.util.BackgroundExecutor;
import dev.texturemixer.core.util.Log;
import dev.texturemixer.fabric.compat.VersionCompat;
import dev.texturemixer.fabric.mixin.PackScreenAccessor;
import dev.texturemixer.fabric.pack.GamePackSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.pack.PackScreen;
import net.minecraft.client.gui.screen.pack.ResourcePackOrganizer;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Owns Texture Mixer's long-lived state for the running game: the texture index (kept
 * between screen openings and rebuilt only when the resourcepacks folder changes), the
 * profile store, the editing session and the preview cache. Also performs Apply and
 * Export, which is the only code that touches Minecraft's pack manager.
 */
public final class TextureMixerManager {
    private static TextureMixerManager instance;

    private final MinecraftClient client;
    private final Path workDirectory;
    private final Path exportDirectory;
    private final PreviewCache previews;
    private final IndexCache indexCache = new IndexCache();

    private ProfileStore profiles;
    private MixerSession session;
    private TextureIndex index;
    private CompletableFuture<TextureIndex> scan;
    private volatile String scanStatus = "";
    private volatile boolean cancelScan;

    private TextureMixerManager(MinecraftClient client) {
        this.client = client;
        this.workDirectory = FabricLoader.getInstance().getGameDir().resolve("texturemixer");
        this.exportDirectory = workDirectory.resolve("exports");
        this.previews = new PreviewCache(client);
    }

    public static TextureMixerManager get() {
        if (instance == null) {
            instance = new TextureMixerManager(MinecraftClient.getInstance());
        }
        return instance;
    }

    public PreviewCache previews() {
        return previews;
    }

    public Path exportDirectory() {
        return exportDirectory;
    }

    public Path resourcePackDirectory() {
        return client.getResourcePackDir();
    }

    /** The editing session; created on first use and kept for the rest of the game session. */
    public MixerSession session() {
        if (session == null) {
            profiles = ProfileStore.load(FabricLoader.getInstance().getConfigDir().resolve("texturemixer").resolve("profiles.json"));
            session = new MixerSession(profiles, new Random());
            // If the generated pack on disk already matches this profile, nothing is pending.
            PackGenerator.GeneratedInfo info = PackGenerator.readGeneratedInfo(
                    resourcePackDirectory().resolve(TextureMixerConstants.GENERATED_PACK_NAME));
            if (info != null && info.profile().equals(session.activeProfile())
                    && info.selectionsDigest().equals(session.selections().digest())
                    && VersionCompat.enabledPackIds(client.getResourcePackManager()).contains(TextureMixerConstants.GENERATED_PACK_ID)) {
                session.markApplied(session.selections());
            }
        }
        return session;
    }

    public TextureIndex currentIndex() {
        return index;
    }

    public boolean isScanning() {
        return scan != null && !scan.isDone();
    }

    /** Short description of what the background scan is doing, for the status line. */
    public String scanStatus() {
        return scanStatus;
    }

    /**
     * Returns an index of every available pack: Minecraft's own textures, the game's
     * built-in and mod packs, and everything in the resourcepacks folder. Reuses the
     * previous index when none of these changed; otherwise scans in the background.
     * The future completes on the render thread.
     */
    public CompletableFuture<TextureIndex> refreshIndex(boolean force) {
        if (scan != null && !scan.isDone()) {
            return scan;
        }
        Path directory = resourcePackDirectory();
        TextureIndex previous = index;
        GamePackSource vanilla = GamePackSource.vanilla(client.getDefaultResourcePack());
        List<ResourcePackProfile> gameProfiles = gameProfiles();
        StringBuilder gameIds = new StringBuilder();
        for (ResourcePackProfile profile : gameProfiles) {
            gameIds.append(VersionCompat.profileId(profile)).append(',');
        }
        cancelScan = false;
        scanStatus = "Checking resource packs";

        CompletableFuture<TextureIndex> background = CompletableFuture.supplyAsync(() -> {
            String fingerprint = gameIds + "|" + PackScanner.fingerprint(directory);
            if (!force && previous != null && fingerprint.equals(previous.fingerprint())) {
                return previous;
            }
            long start = System.nanoTime();
            if (force) {
                indexCache.clear();
            }
            ScanResult result = PackScanner.scan(directory);
            List<PackSource> sources = new ArrayList<>();
            sources.add(vanilla);
            sources.addAll(openGamePacks(gameProfiles, result.problems()));
            sources.addAll(result.packs());
            try {
                TextureIndex built = new TextureIndexer(VersionCompat.packFormat(),
                        (done, total, name) -> scanStatus = done < total ? "Reading pack " + (done + 1) + "/" + total + ": " + name : "",
                        () -> cancelScan)
                        .build(sources, result.problems(), fingerprint, indexCache);
                Log.LOGGER.info("Texture scan finished in {} ms", (System.nanoTime() - start) / 1_000_000);
                return built;
            } catch (RuntimeException e) {
                for (PackSource source : sources) {
                    try {
                        source.close();
                    } catch (IOException closeFailure) {
                        e.addSuppressed(closeFailure);
                    }
                }
                throw e;
            }
        }, BackgroundExecutor.IO);

        scan = background.thenApplyAsync(built -> {
            if (built != previous) {
                previews.clear();
                if (previous != null) {
                    previous.close();
                }
                index = built;
                for (PackProblem problem : built.problems()) {
                    Log.LOGGER.warn("Pack problem: {}", problem);
                }
            }
            scanStatus = "";
            return built;
        }, client);
        scan.whenComplete((built, error) -> {
            if (error != null) {
                scanStatus = "";
                Log.LOGGER.error("Texture scan failed", error);
            }
        });
        return scan;
    }

    /**
     * Packs the game provides besides the resourcepacks folder: the built-in optional
     * packs (Programmer Art, High Contrast) and one pack per mod with resources. Fabric's
     * "Fabric Mods" group is skipped because it only repeats the per-mod packs.
     */
    private List<ResourcePackProfile> gameProfiles() {
        List<ResourcePackProfile> profiles = new ArrayList<>();
        for (ResourcePackProfile profile : client.getResourcePackManager().getProfiles()) {
            String id = VersionCompat.profileId(profile);
            if (id.startsWith("file/") || id.equals(TextureMixerConstants.VANILLA_SOURCE_ID) || id.equals("fabric")) {
                continue;
            }
            profiles.add(profile);
        }
        return profiles;
    }

    private static List<PackSource> openGamePacks(List<ResourcePackProfile> profiles, List<PackProblem> problems) {
        List<PackSource> sources = new ArrayList<>();
        for (ResourcePackProfile profile : profiles) {
            String id = VersionCompat.profileId(profile);
            String name = profile.getDisplayName().getString();
            try {
                sources.add(GamePackSource.opened(profile.createResourcePack(), id, name));
            } catch (RuntimeException e) {
                Log.LOGGER.warn("Could not open built-in pack {}", id, e);
                problems.add(PackProblem.warning(name, "Built-in pack could not be opened: " + e.getMessage()));
            }
        }
        return sources;
    }

    /** Stops a running scan; used when the game shuts down. */
    public void cancelScan() {
        cancelScan = true;
    }

    /**
     * Writes the generated pack into the resourcepacks folder, enables it above the
     * player's other packs and reloads resources. The player's own pack list is kept;
     * the generated pack is only added (or left where the player put it).
     *
     * @param packScreen the resource pack screen to keep in sync, or {@code null}
     */
    public CompletableFuture<GenerationResult> apply(Screen packScreen) {
        TextureIndex currentIndex = requireIndex();
        Selections selections = session().selections();
        GenerationOptions options = options();
        Path target = resourcePackDirectory().resolve(TextureMixerConstants.GENERATED_PACK_NAME);

        return CompletableFuture.supplyAsync(() -> {
            try (DirectoryPackWriter writer = new DirectoryPackWriter(target, workDirectory)) {
                return PackGenerator.generate(selections, currentIndex, writer, options);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, BackgroundExecutor.IO).thenApplyAsync(result -> {
            activateGeneratedPack(packScreen);
            session().markApplied(selections);
            return result;
        }, client);
    }

    /** Exports the current selections as a standalone ZIP resource pack. */
    public CompletableFuture<GenerationResult> export() {
        TextureIndex currentIndex = requireIndex();
        Selections selections = session().selections();
        GenerationOptions options = options();
        String name = "Texture Mixer - " + sanitize(session().activeProfile()) + " - "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss")) + ".zip";
        Path target = exportDirectory.resolve(name);

        return CompletableFuture.supplyAsync(() -> {
            try (ZipPackWriter writer = new ZipPackWriter(target)) {
                return PackGenerator.generate(selections, currentIndex, writer, options);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, BackgroundExecutor.IO);
    }

    private TextureIndex requireIndex() {
        if (index == null) {
            throw new IllegalStateException("Resource packs have not been scanned yet");
        }
        return index;
    }

    private GenerationOptions options() {
        return new GenerationOptions(VersionCompat.packFormat(), session().activeProfile(), gameVersion(), icon());
    }

    private void activateGeneratedPack(Screen packScreen) {
        ResourcePackManager manager = client.getResourcePackManager();
        manager.scanPacks();
        if (manager.getProfile(TextureMixerConstants.GENERATED_PACK_ID) == null) {
            throw new IllegalStateException("Minecraft did not detect the generated pack at "
                    + resourcePackDirectory().resolve(TextureMixerConstants.GENERATED_PACK_NAME));
        }

        List<String> enabled = new ArrayList<>(VersionCompat.enabledPackIds(manager));
        boolean alreadyEnabled = enabled.contains(TextureMixerConstants.GENERATED_PACK_ID);
        if (!alreadyEnabled) {
            // Last = highest priority, so the mixed textures win over every other pack.
            enabled.add(TextureMixerConstants.GENERATED_PACK_ID);
            manager.setEnabledProfiles(enabled);
        }
        // Saves options.txt and reloads if the pack list changed...
        client.options.refreshResourcePacks(manager);
        if (alreadyEnabled) {
            // ...but the list is unchanged when the pack was already on, and its files are new.
            client.reloadResources();
        }

        if (packScreen instanceof PackScreen screen) {
            syncPackScreen(screen);
        }
        Log.LOGGER.info("Activated {} ({} enabled pack(s))", TextureMixerConstants.GENERATED_PACK_ID, enabled.size());
    }

    /**
     * The open resource pack screen applies its own list when closed. Make sure that list
     * contains the generated pack, without discarding changes the player made there.
     */
    private void syncPackScreen(PackScreen screen) {
        try {
            ResourcePackOrganizer organizer = ((PackScreenAccessor) screen).texturemixer$getOrganizer();
            organizer.refresh();
            organizer.getDisabledPacks()
                    .filter(pack -> pack.getDisplayName().getString().equals(TextureMixerConstants.GENERATED_PACK_NAME))
                    .findFirst()
                    .ifPresent(ResourcePackOrganizer.Pack::enable);
        } catch (RuntimeException e) {
            Log.LOGGER.warn("Could not update the open resource pack screen; reopen it if the generated pack shows as disabled", e);
        }
    }

    /** True if {@code screen} is the resource pack screen (not the data pack one). */
    public boolean isResourcePackScreen(Screen screen) {
        if (!(screen instanceof PackScreen)) {
            return false;
        }
        try {
            Path file = ((PackScreenAccessor) screen).texturemixer$getFile();
            return file != null && file.toAbsolutePath().normalize().equals(resourcePackDirectory().toAbsolutePath().normalize());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static String gameVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static byte[] icon() {
        try (InputStream in = TextureMixerManager.class.getResourceAsStream("/assets/texturemixer/icon.png")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9 _.-]", "_");
    }
}
