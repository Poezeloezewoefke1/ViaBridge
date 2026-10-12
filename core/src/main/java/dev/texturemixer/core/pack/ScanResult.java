package dev.texturemixer.core.pack;

import dev.texturemixer.core.util.Log;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

/**
 * Packs found in the resourcepacks directory, plus everything that could not be read.
 * Owns the opened sources (ZIP files stay open until {@link #close()}).
 */
public record ScanResult(List<PackSource> packs, List<PackProblem> problems) implements Closeable {
    @Override
    public void close() {
        for (PackSource pack : packs) {
            try {
                pack.close();
            } catch (IOException e) {
                Log.LOGGER.warn("Failed to close {}", pack, e);
            }
        }
    }
}
