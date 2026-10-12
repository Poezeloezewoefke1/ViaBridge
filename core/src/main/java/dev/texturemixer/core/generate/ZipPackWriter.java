package dev.texturemixer.core.generate;

import dev.texturemixer.core.pack.SafePaths;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes the generated pack as a normal ZIP resource pack, for exporting and sharing.
 * The archive is built under a temporary name and renamed when complete.
 */
public final class ZipPackWriter implements PackWriter {
    private final Path target;
    private final Path temp;
    private final ZipOutputStream zip;
    private final Set<String> written = new HashSet<>();
    private boolean committed;

    public ZipPackWriter(Path target) throws IOException {
        this.target = target.toAbsolutePath().normalize();
        Files.createDirectories(this.target.getParent());
        this.temp = this.target.resolveSibling(this.target.getFileName() + ".partial");
        OutputStream out = Files.newOutputStream(temp);
        this.zip = new ZipOutputStream(out);
    }

    @Override
    public void write(String path, byte[] data) throws IOException {
        String safe = SafePaths.normalize(path);
        if (safe == null) {
            throw new IOException("Refusing to write unsafe path " + path);
        }
        if (!written.add(safe)) {
            throw new IOException("Duplicate entry " + safe);
        }
        ZipEntry entry = new ZipEntry(safe);
        // Fixed timestamps keep exports byte-identical for identical selections.
        entry.setLastModifiedTime(FileTime.fromMillis(315532800000L));
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    @Override
    public void commit() throws IOException {
        zip.close();
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        committed = true;
    }

    @Override
    public String describeTarget() {
        return target.toString();
    }

    @Override
    public void close() throws IOException {
        if (!committed) {
            try {
                zip.close();
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }
}
