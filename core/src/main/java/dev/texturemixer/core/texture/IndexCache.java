package dev.texturemixer.core.texture;

import dev.texturemixer.core.pack.PackSource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers what each pack contributed to the last index, keyed by the pack's
 * fingerprint (file sizes and modification times). A rescan after adding or editing one
 * pack then only reads that pack; Minecraft's own textures are read once per session.
 */
public final class IndexCache {
    private record Entry(String key, TextureIndexer.SourceResult result) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    TextureIndexer.SourceResult get(String sourceId, String key) {
        Entry entry = entries.get(sourceId);
        return entry != null && entry.key().equals(key) ? entry.result() : null;
    }

    void put(String sourceId, String key, TextureIndexer.SourceResult result) {
        entries.put(sourceId, new Entry(key, result));
    }

    /** Drops packs that are no longer installed. */
    void retainOnly(List<PackSource> sources) {
        Set<String> ids = new HashSet<>();
        for (PackSource source : sources) {
            ids.add(source.id());
        }
        entries.keySet().retainAll(ids);
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
