package dev.texturemixer.fabric.test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes the resource packs used by the game test into the resourcepacks folder. */
final class TestPacks {
    static final byte[] RED_16 = png(16, 16, 0xE02020FF);
    static final byte[] BLUE_32 = png(32, 32, 0x2040E0FF);

    private TestPacks() {
    }

    static void write(Path directory, int format) throws IOException {
        String mcmeta = "{\"pack\":{\"pack_format\":" + format + ",\"description\":\"Texture Mixer test pack\"}}";

        Path red = directory.resolve("TM Test Red");
        write(red.resolve("pack.mcmeta"), mcmeta.getBytes(StandardCharsets.UTF_8));
        write(red.resolve("assets/minecraft/textures/block/stone.png"), RED_16);
        write(red.resolve("assets/minecraft/textures/item/diamond_sword.png"), RED_16);

        Map<String, byte[]> blue = new LinkedHashMap<>();
        blue.put("pack.mcmeta", mcmeta.getBytes(StandardCharsets.UTF_8));
        blue.put("assets/minecraft/textures/block/stone.png", BLUE_32);
        blue.put("assets/minecraft/textures/block/water_still.png", png(16, 64, 0x2040E0C0));
        blue.put("assets/minecraft/textures/block/water_still.png.mcmeta", "{\"animation\":{\"frametime\":2}}".getBytes(StandardCharsets.UTF_8));
        blue.put("assets/minecraft/textures/entity/zombie/zombie.png", png(64, 64, 0x20A020FF));
        blue.put("assets/minecraft/textures/item/diamond_sword.png", BLUE_32);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(directory.resolve("TM Test Blue.zip")))) {
            for (Map.Entry<String, byte[]> entry : blue.entrySet()) {
                out.putNextEntry(new ZipEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }

        Files.writeString(directory.resolve("TM Broken.zip"), "this is not a zip file");
    }

    private static void write(Path file, byte[] data) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, data);
    }

    static byte[] png(int width, int height, int rgba) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            for (int y = 0; y < height; y++) {
                raw.write(0);
                for (int x = 0; x < width; x++) {
                    int shade = ((x / 4 + y / 4) % 2 == 0) ? 0 : 40;
                    raw.write(Math.max(0, (rgba >>> 24) - shade));
                    raw.write(Math.max(0, ((rgba >>> 16) & 0xFF) - shade));
                    raw.write(Math.max(0, ((rgba >>> 8) & 0xFF) - shade));
                    raw.write(rgba & 0xFF);
                }
            }
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
                deflater.write(raw.toByteArray());
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            DataOutputStream ihdr = new DataOutputStream(header);
            ihdr.writeInt(width);
            ihdr.writeInt(height);
            ihdr.write(new byte[]{8, 6, 0, 0, 0});
            chunk(out, "IHDR", header.toByteArray());
            chunk(out, "IDAT", compressed.toByteArray());
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
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
}
