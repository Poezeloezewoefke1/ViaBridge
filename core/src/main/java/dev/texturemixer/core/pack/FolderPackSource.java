package dev.texturemixer.core.pack;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.stream.Stream;

/** A pack stored as a plain folder. Opened read-only; nothing is ever written to it. */
public final class FolderPackSource implements PackSource {
    private final Path root;
    private final String name;
    private final PackMetadata metadata;

    public FolderPackSource(Path root, PackMetadata metadata) {
        this.root = root.toAbsolutePath().normalize();
        this.name = root.getFileName().toString();
        this.metadata = metadata;
    }

    public Path root() {
        return root;
    }

    @Override
    public String id() {
        return "file/" + name;
    }

    @Override
    public String displayName() {
        return name;
    }

    @Override
    public PackKind kind() {
        return PackKind.FOLDER;
    }

    @Override
    public PackMetadata metadata() {
        return metadata;
    }

    @Override
    public void forEachFile(String prefix, FileVisitor visitor) throws IOException {
        Path start = resolve(prefix);
        if (start == null || !Files.isDirectory(start)) {
            return;
        }
        try (Stream<Path> files = Files.walk(start)) {
            Iterator<Path> iterator = files.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                BasicFileAttributes attributes;
                try {
                    attributes = Files.readAttributes(file, BasicFileAttributes.class);
                } catch (IOException e) {
                    continue;
                }
                if (!attributes.isRegularFile()) {
                    continue;
                }
                String relative = SafePaths.normalize(root.relativize(file).toString());
                if (relative != null) {
                    visitor.visit(relative, attributes.size());
                }
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public InputStream open(String path) throws IOException {
        Path file = resolve(path);
        if (file == null || !Files.isRegularFile(file)) {
            throw new FileNotFoundException(path + " in " + name);
        }
        return Files.newInputStream(file);
    }

    @Override
    public boolean exists(String path) {
        Path file = resolve(path);
        return file != null && Files.isRegularFile(file);
    }

    @Override
    public String fingerprint() {
        long count = 0;
        long totalSize = 0;
        long newest = 0;
        try (Stream<Path> files = Files.walk(root)) {
            Iterator<Path> iterator = files.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                try {
                    BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                    count++;
                    totalSize += attributes.size();
                    newest = Math.max(newest, attributes.lastModifiedTime().toMillis());
                } catch (IOException ignored) {
                    // A file vanished while walking; the next fingerprint will differ anyway.
                }
            }
        } catch (IOException | UncheckedIOException e) {
            return "folder:" + name + ":unreadable";
        }
        return "folder:" + name + ":" + count + ":" + totalSize + ":" + newest;
    }

    /** Resolves a pack-relative path, refusing anything that would leave the pack folder. */
    private Path resolve(String path) {
        String safe = SafePaths.normalize(path);
        if (safe == null) {
            return null;
        }
        Path resolved = root.resolve(safe).normalize();
        return resolved.startsWith(root) ? resolved : null;
    }

    @Override
    public String toString() {
        return "FolderPack[" + name + "]";
    }
}
