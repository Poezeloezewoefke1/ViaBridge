package dev.texturemixer.core.generate;

import java.io.Closeable;
import java.io.IOException;

/**
 * Destination for a generated pack. Nothing becomes visible until {@link #commit()};
 * closing without committing discards everything written so far.
 */
public interface PackWriter extends Closeable {
    /** Writes one file; {@code path} is relative to the pack root. */
    void write(String path, byte[] data) throws IOException;

    void commit() throws IOException;

    /** Human-readable location of the result, for messages. */
    String describeTarget();
}
