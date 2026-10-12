package dev.texturemixer.core.pack;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Read-only view of one resource pack. Implementations never modify the pack.
 *
 * <p>Paths are relative to the pack root and use forward slashes, e.g.
 * {@code assets/minecraft/textures/block/stone.png}.
 */
public interface PackSource extends Closeable {
    /** Stable id. Matches Minecraft's pack id ({@code file/<name>}, or {@code vanilla}). */
    String id();

    /** Name shown to the player. */
    String displayName();

    PackKind kind();

    /** Parsed {@code pack.mcmeta}, or {@link PackMetadata#NONE} for built-in sources. */
    PackMetadata metadata();

    /**
     * Visits every regular file whose path starts with {@code prefix}
     * (for example {@code assets/} or {@code overlay_dir/assets/}).
     */
    void forEachFile(String prefix, FileVisitor visitor) throws IOException;

    /** Opens a file. Throws {@link java.io.FileNotFoundException} if it is absent. */
    InputStream open(String path) throws IOException;

    boolean exists(String path);

    /**
     * A cheap value that changes when the pack's contents change on disk (size and
     * modification time). Used to decide whether a cached index is still valid.
     */
    String fingerprint();

    @Override
    default void close() throws IOException {
    }

    @FunctionalInterface
    interface FileVisitor {
        void visit(String path, long size) throws IOException;
    }
}
