package dev.texturemixer.core.texture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.texturemixer.core.pack.PackKind;
import dev.texturemixer.core.pack.PackMetadata;
import dev.texturemixer.core.pack.PackProblem;
import dev.texturemixer.core.pack.PackSource;
import dev.texturemixer.core.util.BackgroundExecutor;
import dev.texturemixer.core.util.BoundedReader;
import dev.texturemixer.core.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Builds a {@link TextureIndex} from a set of packs. Only PNG headers and small
 * {@code .mcmeta} files are read; image data is left alone until a preview is needed.
 */
public final class TextureIndexer {
    /** Pack format from which 1.13's renamed texture folders apply. */
    private static final int FLATTENING_FORMAT = 4;
    private static final long MAX_MCMETA_BYTES = 1024 * 1024;

    private final int gamePackFormat;
    private final ProgressListener progress;
    private final BooleanSupplier cancelled;

    /**
     * @param gamePackFormat resource pack format of the running Minecraft version, used to
     *                       pick active overlays and to flag packs made for other versions
     */
    public TextureIndexer(int gamePackFormat, ProgressListener progress, BooleanSupplier cancelled) {
        this.gamePackFormat = gamePackFormat;
        this.progress = progress;
        this.cancelled = cancelled;
    }

    public TextureIndex build(List<PackSource> sources, List<PackProblem> scanProblems, String fingerprint) {
        return build(sources, scanProblems, fingerprint, null);
    }

    /**
     * Builds the index. Packs are indexed in parallel; packs whose files have not changed
     * since the previous scan are taken from {@code cache} instead of being read again.
     *
     * @param cache per-pack results from earlier scans, or {@code null}
     */
    public TextureIndex build(List<PackSource> sources, List<PackProblem> scanProblems, String fingerprint, IndexCache cache) {
        List<PackProblem> problems = new ArrayList<>(scanProblems);
        AtomicInteger done = new AtomicInteger();
        progress.onProgress(0, sources.size(), sources.isEmpty() ? "" : sources.get(0).displayName());

        List<Future<SourceResult>> futures = new ArrayList<>();
        for (PackSource source : sources) {
            futures.add(BackgroundExecutor.INDEX.submit(() -> {
                if (cancelled.getAsBoolean()) {
                    throw new CancellationException("Texture scan cancelled");
                }
                String key = source.fingerprint() + "|" + gamePackFormat;
                SourceResult result = cache == null ? null : cache.get(source.id(), key);
                if (result == null) {
                    result = indexSafely(source);
                    if (cache != null) {
                        cache.put(source.id(), key, result);
                    }
                }
                progress.onProgress(done.incrementAndGet(), sources.size(), source.displayName());
                return result;
            }));
        }

        // Merge in source order, so variants keep the "Minecraft first, then packs" order.
        Map<TextureId, List<TextureVariant>> variants = new HashMap<>();
        try {
            for (Future<SourceResult> future : futures) {
                SourceResult result = future.get();
                problems.addAll(result.problems());
                for (TextureVariant variant : result.variants()) {
                    variants.computeIfAbsent(variant.id(), key -> new ArrayList<>(2)).add(variant);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            futures.forEach(future -> future.cancel(true));
            throw new CancellationException("Texture scan interrupted");
        } catch (ExecutionException e) {
            futures.forEach(future -> future.cancel(true));
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException(e.getCause());
        }
        if (cache != null) {
            cache.retainOnly(sources);
        }
        progress.onProgress(sources.size(), sources.size(), "");

        TreeMap<TextureId, TextureEntry> entries = new TreeMap<>();
        for (Map.Entry<TextureId, List<TextureVariant>> entry : variants.entrySet()) {
            entries.put(entry.getKey(), new TextureEntry(entry.getKey(), TextureCategory.classify(entry.getKey()), List.copyOf(entry.getValue())));
        }
        Log.LOGGER.info("Indexed {} textures from {} source(s)", entries.size(), sources.size());
        return new TextureIndex(entries, sources, problems, fingerprint);
    }

    private SourceResult indexSafely(PackSource source) {
        try {
            return indexSource(source);
        } catch (IOException | RuntimeException e) {
            if (e instanceof CancellationException cancellation) {
                throw cancellation;
            }
            Log.LOGGER.warn("Failed to index {}", source, e);
            return new SourceResult(List.of(), List.of(PackProblem.error(source.displayName(), "Textures could not be read: " + e.getMessage())));
        }
    }

    /** What one pack contributes to the index. Immutable, so it can be cached between scans. */
    public record SourceResult(List<TextureVariant> variants, List<PackProblem> problems) {
    }

    private SourceResult indexSource(PackSource source) throws IOException {
        List<PackProblem> problems = new ArrayList<>();
        List<TextureVariant> variants = new ArrayList<>();
        PackMetadata metadata = source.metadata();
        boolean builtin = source.kind() == PackKind.BUILTIN;

        if (!builtin && metadata.packFormat() >= 0 && gamePackFormat > 0 && !metadata.supports(gamePackFormat)) {
            problems.add(PackProblem.warning(source.displayName(), "Made for a different Minecraft version (pack format "
                    + metadata.packFormat() + ", this game uses " + gamePackFormat
                    + "). Textures whose names changed between versions will not apply."));
        }

        // Base files first, then each active overlay replacing them, exactly as Minecraft stacks them.
        Map<TextureId, String> files = new HashMap<>();
        Set<String> allFiles = new HashSet<>();
        int[] invalidNames = {0};
        collect(source, "", files, allFiles, invalidNames);
        if (!builtin) {
            for (PackMetadata.Overlay overlay : metadata.activeOverlays(gamePackFormat)) {
                collect(source, overlay.directory() + "/", files, allFiles, invalidNames);
            }
        }
        if (invalidNames[0] > 0) {
            problems.add(PackProblem.warning(source.displayName(), invalidNames[0]
                    + " texture file(s) have names Minecraft does not accept (upper-case letters, spaces, ...) and were skipped"));
        }

        int unreadable = 0;
        int legacy = 0;
        int processed = 0;
        for (Map.Entry<TextureId, String> file : files.entrySet()) {
            if ((++processed & 255) == 0 && cancelled.getAsBoolean()) {
                throw new CancellationException("Texture scan cancelled");
            }
            TextureId id = file.getKey();
            String pngPath = file.getValue();
            PngInfo info = readHeader(source, pngPath);
            if (info == null) {
                unreadable++;
                continue;
            }
            String metadataPath = allFiles.contains(pngPath + ".mcmeta") ? pngPath + ".mcmeta" : null;
            int frameWidth = info.width();
            int frameHeight = info.height();
            boolean animated = false;
            int frameTime = 1;
            if (metadataPath != null) {
                JsonObject animation = readAnimation(source, metadataPath);
                if (animation != null) {
                    animated = true;
                    frameTime = Math.max(1, intOr(animation, "frametime", 1));
                    int square = Math.min(info.width(), info.height());
                    frameWidth = intOr(animation, "width", square);
                    frameHeight = intOr(animation, "height", square);
                    if (frameWidth <= 0 || frameHeight <= 0 || frameWidth > info.width() || frameHeight > info.height()) {
                        frameWidth = square;
                        frameHeight = square;
                    }
                }
            }
            boolean legacyPath = !builtin && gamePackFormat >= FLATTENING_FORMAT && TextureCategory.isLegacyPath(id);
            if (legacyPath) {
                legacy++;
            }
            variants.add(new TextureVariant(id, source.id(), pngPath, metadataPath, info.width(), info.height(),
                    frameWidth, frameHeight, animated, frameTime, legacyPath));
        }
        if (unreadable > 0) {
            problems.add(PackProblem.warning(source.displayName(), unreadable + " texture(s) are not valid PNG images and were skipped"));
        }
        if (legacy > 0) {
            problems.add(PackProblem.warning(source.displayName(), legacy
                    + " texture(s) use pre-1.13 folder names (blocks/, items/). They are listed but this Minecraft version will not use them."));
        }
        return new SourceResult(List.copyOf(variants), List.copyOf(problems));
    }

    private static void collect(PackSource source, String prefix, Map<TextureId, String> files,
                                Set<String> allFiles, int[] invalidNames) throws IOException {
        source.forEachFile(prefix + "assets/", (path, size) -> {
            allFiles.add(path);
            String assetPath = path.substring(prefix.length());
            if (!assetPath.endsWith(".png")) {
                if (TextureId.looksLikeInvalidTexture(assetPath)) {
                    invalidNames[0]++;
                }
                return;
            }
            TextureId id = TextureId.fromAssetPath(assetPath);
            if (id == null) {
                if (TextureId.looksLikeInvalidTexture(assetPath)) {
                    invalidNames[0]++;
                }
                return;
            }
            files.put(id, path);
        });
    }

    private static PngInfo readHeader(PackSource source, String path) {
        try (InputStream in = source.open(path)) {
            return PngInfo.parse(BoundedReader.readPrefix(in, PngInfo.HEADER_BYTES));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Returns the {@code animation} object of a texture's .mcmeta, or null if there is none. */
    private static JsonObject readAnimation(PackSource source, String path) {
        try (InputStream in = source.open(path)) {
            String json = new String(BoundedReader.readAll(in, MAX_MCMETA_BYTES, path), StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonObject() && root.getAsJsonObject().has("animation") && root.getAsJsonObject().get("animation").isJsonObject()) {
                return root.getAsJsonObject().getAsJsonObject("animation");
            }
        } catch (IOException | RuntimeException e) {
            Log.LOGGER.debug("Unreadable texture metadata {} in {}", path, source.displayName(), e);
        }
        return null;
    }

    private static int intOr(JsonObject object, String key, int fallback) {
        try {
            return object.has(key) ? object.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int done, int total, String current);
    }
}
