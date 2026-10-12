package dev.texturemixer.core;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds small but real resource packs for tests. */
public final class TestPacks {
    private TestPacks() {
    }

    /** A valid RGBA PNG of the given size filled with one colour. */
    public static byte[] png(int width, int height, int rgba) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            for (int y = 0; y < height; y++) {
                raw.write(0);
                for (int x = 0; x < width; x++) {
                    raw.write(rgba >>> 24);
                    raw.write(rgba >>> 16);
                    raw.write(rgba >>> 8);
                    raw.write(rgba);
                }
            }
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
                deflater.write(raw.toByteArray());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream header = new DataOutputStream(ihdr);
            header.writeInt(width);
            header.writeInt(height);
            header.write(new byte[]{8, 6, 0, 0, 0});
            chunk(out, "IHDR", ihdr.toByteArray());
            chunk(out, "IDAT", compressed.toByteArray());
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static void chunk(OutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream stream = new DataOutputStream(out);
        stream.writeInt(data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        stream.write(typeBytes);
        stream.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        stream.writeInt((int) crc.getValue());
    }

    public static String mcmeta(int format) {
        return "{\"pack\":{\"pack_format\":" + format + ",\"description\":\"test\"}}";
    }

    public static Builder pack() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, byte[]> files = new LinkedHashMap<>();

        public Builder file(String path, byte[] data) {
            files.put(path, data);
            return this;
        }

        public Builder file(String path, String text) {
            return file(path, text.getBytes(StandardCharsets.UTF_8));
        }

        public Builder texture(String id, int size, int rgba) {
            int colon = id.indexOf(':');
            return file("assets/" + id.substring(0, colon) + "/textures/" + id.substring(colon + 1) + ".png", png(size, size, rgba));
        }

        public Path writeFolder(Path directory) throws IOException {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                Path target = directory.resolve(file.getKey());
                Files.createDirectories(target.getParent());
                Files.write(target, file.getValue());
            }
            return directory;
        }

        public Path writeZip(Path zip) throws IOException {
            Files.createDirectories(zip.getParent());
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                for (Map.Entry<String, byte[]> file : files.entrySet()) {
                    out.putNextEntry(new ZipEntry(file.getKey()));
                    out.write(file.getValue());
                    out.closeEntry();
                }
            }
            return zip;
        }
    }
}
