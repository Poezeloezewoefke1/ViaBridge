package dev.texturemixer.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Reads streams fully while refusing to go past a size limit (guards against ZIP bombs). */
public final class BoundedReader {
    private BoundedReader() {
    }

    public static byte[] readAll(InputStream in, long maxBytes, String what) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException(what + " is larger than the " + (maxBytes / (1024 * 1024)) + " MB limit");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /** Reads up to {@code count} bytes; returns fewer only if the stream ends first. */
    public static byte[] readPrefix(InputStream in, int count) throws IOException {
        byte[] buffer = new byte[count];
        int offset = 0;
        while (offset < count) {
            int read = in.read(buffer, offset, count - offset);
            if (read == -1) {
                byte[] shorter = new byte[offset];
                System.arraycopy(buffer, 0, shorter, 0, offset);
                return shorter;
            }
            offset += read;
        }
        return buffer;
    }
}
