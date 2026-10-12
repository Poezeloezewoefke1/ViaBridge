package dev.texturemixer.core.pack;

import dev.texturemixer.core.TextureMixerConstants;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * A pack stored as a ZIP file. The archive is opened read-only and its entry names are
 * validated up front: entries that would escape the pack root ({@code ../}, absolute
 * paths) are ignored and reported, and archives with an absurd number of entries are
 * rejected as malformed.
 */
public final class ZipPackSource implements PackSource {
    private final Path file;
    private final String name;
    private final ZipFile zip;
    private final Map<String, ZipEntry> entries;
    private final int unsafeEntries;
    private PackMetadata metadata = PackMetadata.NONE;

    private ZipPackSource(Path file, ZipFile zip, Map<String, ZipEntry> entries, int unsafeEntries) {
        this.file = file;
        this.name = file.getFileName().toString();
        this.zip = zip;
        this.entries = entries;
        this.unsafeEntries = unsafeEntries;
    }

    /**
     * Opens and validates a ZIP pack.
     *
     * @throws IOException with a player-readable message if the archive is unusable
     */
    public static ZipPackSource open(Path file) throws IOException {
        ZipFile zip;
        try {
            zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8);
        } catch (ZipException utf8Failure) {
            // Archives created by some Windows tools store names in the legacy DOS code page.
            try {
                zip = new ZipFile(file.toFile(), Charset.forName("IBM437"));
            } catch (ZipException e) {
                throw new IOException("not a valid ZIP file (" + utf8Failure.getMessage() + ")");
            }
        }
        try {
            Map<String, ZipEntry> entries = new HashMap<>();
            int unsafe = 0;
            int count = 0;
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (++count > TextureMixerConstants.MAX_ZIP_ENTRIES) {
                    throw new IOException("ZIP has more than " + TextureMixerConstants.MAX_ZIP_ENTRIES + " entries");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String normalized = SafePaths.normalize(entry.getName());
                if (normalized == null) {
                    unsafe++;
                    continue;
                }
                entries.putIfAbsent(normalized, entry);
            }
            return new ZipPackSource(file, zip, Collections.unmodifiableMap(entries), unsafe);
        } catch (IOException | RuntimeException e) {
            zip.close();
            throw e instanceof IOException io ? io : new IOException("ZIP could not be read: " + e.getMessage(), e);
        }
    }

    void setMetadata(PackMetadata metadata) {
        this.metadata = metadata;
    }

    /** Number of entries skipped because their names were unsafe. */
    public int unsafeEntries() {
        return unsafeEntries;
    }

    /** True if the archive holds {@code pack.mcmeta} only inside a single sub-folder. */
    public String nestedRootFolder() {
        for (String path : entries.keySet()) {
            int slash = path.indexOf('/');
            if (slash > 0 && path.substring(slash + 1).equals("pack.mcmeta")) {
                return path.substring(0, slash);
            }
        }
        return null;
    }

    @Override
    public String id() {
        return "file/" + name;
    }

    @Override
    public String displayName() {
        return name.endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
    }

    @Override
    public PackKind kind() {
        return PackKind.ZIP;
    }

    @Override
    public PackMetadata metadata() {
        return metadata;
    }

    @Override
    public void forEachFile(String prefix, FileVisitor visitor) throws IOException {
        for (Map.Entry<String, ZipEntry> entry : entries.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                visitor.visit(entry.getKey(), entry.getValue().getSize());
            }
        }
    }

    @Override
    public InputStream open(String path) throws IOException {
        String safe = SafePaths.normalize(path);
        ZipEntry entry = safe == null ? null : entries.get(safe);
        if (entry == null) {
            throw new FileNotFoundException(path + " in " + name);
        }
        if (entry.getSize() > TextureMixerConstants.MAX_ASSET_BYTES) {
            throw new IOException(path + " in " + name + " is too large");
        }
        return zip.getInputStream(entry);
    }

    @Override
    public boolean exists(String path) {
        String safe = SafePaths.normalize(path);
        return safe != null && entries.containsKey(safe);
    }

    @Override
    public String fingerprint() {
        try {
            return "zip:" + name + ":" + Files.size(file) + ":" + Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return "zip:" + name + ":unreadable";
        }
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    @Override
    public String toString() {
        return "ZipPack[" + name + "]";
    }
}
