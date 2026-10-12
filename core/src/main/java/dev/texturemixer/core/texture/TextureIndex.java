package dev.texturemixer.core.texture;

import dev.texturemixer.core.pack.PackProblem;
import dev.texturemixer.core.pack.PackSource;
import dev.texturemixer.core.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * Every texture found across all scanned packs, mapped to the packs that provide it.
 * Built once per scan in the background and then only read; it holds metadata only,
 * never decoded images, so even very large packs cost little memory.
 */
public final class TextureIndex implements Closeable {
    private final SortedMap<TextureId, TextureEntry> entries;
    private final Map<String, PackSource> sources;
    private final List<PackProblem> problems;
    private final String fingerprint;

    TextureIndex(SortedMap<TextureId, TextureEntry> entries, List<PackSource> sources,
                 List<PackProblem> problems, String fingerprint) {
        this.entries = Collections.unmodifiableSortedMap(entries);
        Map<String, PackSource> byId = new LinkedHashMap<>();
        for (PackSource source : sources) {
            byId.put(source.id(), source);
        }
        this.sources = Collections.unmodifiableMap(byId);
        this.problems = List.copyOf(problems);
        this.fingerprint = fingerprint;
    }

    public Collection<TextureEntry> entries() {
        return entries.values();
    }

    public TextureEntry get(TextureId id) {
        return entries.get(id);
    }

    public int size() {
        return entries.size();
    }

    /** All sources in index order: Minecraft's own first, then packs by folder name. */
    public Collection<PackSource> sources() {
        return sources.values();
    }

    public PackSource source(String id) {
        return sources.get(id);
    }

    /** Display name for a source id; falls back to the id for packs no longer present. */
    public String sourceName(String id) {
        PackSource source = sources.get(id);
        return source != null ? source.displayName() : id;
    }

    public List<PackProblem> problems() {
        return problems;
    }

    public String fingerprint() {
        return fingerprint;
    }

    /** Number of textures each pack provides, for the pack summary. */
    public Map<String, Integer> texturesPerSource() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String id : sources.keySet()) {
            counts.put(id, 0);
        }
        for (TextureEntry entry : entries.values()) {
            for (TextureVariant variant : entry.variants()) {
                counts.merge(variant.sourceId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    @Override
    public void close() {
        for (PackSource source : new ArrayList<>(sources.values())) {
            try {
                source.close();
            } catch (IOException e) {
                Log.LOGGER.warn("Failed to close {}", source, e);
            }
        }
    }
}
