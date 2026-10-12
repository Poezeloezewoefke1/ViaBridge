package dev.texturemixer.fabric;

import dev.texturemixer.core.TextureMixerConstants;
import dev.texturemixer.core.pack.PackSource;
import dev.texturemixer.core.texture.TextureIndex;
import dev.texturemixer.core.util.BackgroundExecutor;
import dev.texturemixer.core.util.BoundedReader;
import dev.texturemixer.core.util.Log;
import dev.texturemixer.fabric.compat.VersionCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GPU textures for the previews in the selection screen.
 *
 * <p>Each (pack, file) pair is decoded at most once and only when it is actually on
 * screen. Reading and decoding happen on a background thread; only the upload runs on
 * the render thread. Least-recently-drawn previews are released once the cache passes
 * a size budget, and everything is released when the screen closes.
 */
public final class PreviewCache {
    private static final int MAX_ENTRIES = 512;
    private static final long MAX_PIXELS = 24L * 1024 * 1024;
    /** Requests not drawn for this many frames are dropped before decoding (fast scrolling). */
    private static final long STALE_FRAMES = 30;

    private final MinecraftClient client;
    private final LinkedHashMap<String, Slot> slots = new LinkedHashMap<>(256, 0.75f, true);
    private volatile long frame;
    private int generation;
    private int nextId;
    private long pixels;

    PreviewCache(MinecraftClient client) {
        this.client = client;
    }

    /** Call once per rendered frame before drawing previews. */
    public void beginFrame() {
        frame++;
    }

    /**
     * Returns the loaded preview for a file in a pack, or {@code null} while it is
     * loading (or if it could not be decoded). Starts loading on first request.
     */
    public Preview get(TextureIndex index, String sourceId, String path) {
        String key = sourceId + "\n" + path;
        Slot slot = slots.get(key);
        if (slot == null) {
            slot = new Slot();
            slots.put(key, slot);
            slot.lastFrame = frame;
            load(index, key, slot, sourceId, path);
            return null;
        }
        slot.lastFrame = frame;
        return slot.preview;
    }

    private void load(TextureIndex index, String key, Slot slot, String sourceId, String path) {
        int expectedGeneration = generation;
        PackSource source = index.source(sourceId);
        if (source == null) {
            slot.failed = true;
            return;
        }
        BackgroundExecutor.PREVIEWS.execute(() -> {
            if (frame - slot.lastFrame > STALE_FRAMES) {
                client.execute(() -> {
                    if (generation == expectedGeneration && slots.get(key) == slot && slot.preview == null) {
                        slots.remove(key);
                    }
                });
                return;
            }
            NativeImage image;
            try (InputStream in = source.open(path)) {
                byte[] bytes = BoundedReader.readAll(in, TextureMixerConstants.MAX_ASSET_BYTES, path);
                image = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(bytes));
            } catch (Exception e) {
                Log.LOGGER.debug("Preview of {} from {} failed", path, sourceId, e);
                client.execute(() -> slot.failed = true);
                return;
            }
            client.execute(() -> upload(expectedGeneration, key, slot, image));
        });
    }

    private void upload(int expectedGeneration, String key, Slot slot, NativeImage image) {
        if (generation != expectedGeneration || slots.get(key) != slot) {
            image.close();
            return;
        }
        Identifier id = VersionCompat.id("texturemixer", "preview/" + (nextId++));
        int width = image.getWidth();
        int height = image.getHeight();
        try {
            VersionCompat.registerDynamicTexture(client.getTextureManager(), id, image);
        } catch (RuntimeException e) {
            Log.LOGGER.warn("Could not upload preview {}", key, e);
            image.close();
            slot.failed = true;
            return;
        }
        slot.preview = new Preview(id, width, height);
        pixels += (long) width * height;
        evict();
    }

    private void evict() {
        Iterator<Map.Entry<String, Slot>> iterator = slots.entrySet().iterator();
        while ((slots.size() > MAX_ENTRIES || pixels > MAX_PIXELS) && iterator.hasNext()) {
            Slot slot = iterator.next().getValue();
            if (slot.lastFrame >= frame - 1) {
                break; // Everything after this was drawn recently too (access order).
            }
            release(slot);
            iterator.remove();
        }
    }

    private void release(Slot slot) {
        if (slot.preview != null) {
            client.getTextureManager().destroyTexture(slot.preview.id());
            pixels -= (long) slot.preview.width() * slot.preview.height();
            slot.preview = null;
        }
    }

    /** Releases every preview texture. Pending loads are discarded when they finish. */
    public void clear() {
        generation++;
        for (Slot slot : slots.values()) {
            release(slot);
        }
        slots.clear();
        pixels = 0;
    }

    public int size() {
        return slots.size();
    }

    public record Preview(Identifier id, int width, int height) {
    }

    private static final class Slot {
        volatile long lastFrame;
        Preview preview;
        boolean failed;
    }
}
