package dev.texturemixer.core.texture;

/**
 * Size information read from a PNG header, without decoding the image.
 */
public record PngInfo(int width, int height) {
    /** Bytes needed by {@link #parse}: signature (8) + IHDR length/type (8) + width/height (8). */
    public static final int HEADER_BYTES = 24;

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** Largest dimension accepted. Larger images are almost certainly corrupt or hostile. */
    public static final int MAX_DIMENSION = 16384;

    /**
     * Parses the header, returning {@code null} if the bytes are not a PNG with a sane size.
     */
    public static PngInfo parse(byte[] header) {
        if (header == null || header.length < HEADER_BYTES) {
            return null;
        }
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (header[i] != SIGNATURE[i]) {
                return null;
            }
        }
        if (header[12] != 'I' || header[13] != 'H' || header[14] != 'D' || header[15] != 'R') {
            return null;
        }
        int width = readInt(header, 16);
        int height = readInt(header, 20);
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
            return null;
        }
        return new PngInfo(width, height);
    }

    /** A vertical strip of square frames, the usual layout of animated textures. */
    public boolean looksLikeAnimationStrip() {
        return height > width && height % width == 0;
    }

    private static int readInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
    }
}
