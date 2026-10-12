package dev.texturemixer.core.pack;

import dev.texturemixer.core.TextureMixerConstants;
import dev.texturemixer.core.util.BoundedReader;
import dev.texturemixer.core.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finds every resource pack in a directory, enabled or not, without going through
 * Minecraft's pack manager. Both folder and ZIP packs are supported. The pack generated
 * by Texture Mixer itself is skipped so it can never become its own source.
 */
public final class PackScanner {
    private static final long MAX_MCMETA_BYTES = 1024 * 1024;

    private PackScanner() {
    }

    public static ScanResult scan(Path directory) {
        List<PackSource> packs = new ArrayList<>();
        List<PackProblem> problems = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            Log.LOGGER.info("Resource pack directory {} does not exist yet", directory);
            return new ScanResult(packs, problems);
        }

        List<Path> candidates = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            stream.forEach(candidates::add);
        } catch (IOException e) {
            problems.add(PackProblem.error(directory.getFileName().toString(),
                    "Could not list the resource pack folder: " + e.getMessage()));
            return new ScanResult(packs, problems);
        }
        candidates.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));

        for (Path candidate : candidates) {
            String fileName = candidate.getFileName().toString();
            if (fileName.startsWith(".")) {
                continue;
            }
            try {
                if (Files.isDirectory(candidate)) {
                    scanFolder(candidate, packs, problems);
                } else if (fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                    scanZip(candidate, packs, problems);
                }
            } catch (IOException | RuntimeException e) {
                Log.LOGGER.warn("Skipping resource pack {}", fileName, e);
                problems.add(PackProblem.error(fileName, "Could not be read: " + e.getMessage()));
            }
        }
        Log.LOGGER.info("Found {} resource pack(s) in {} ({} problem(s))", packs.size(), directory, problems.size());
        return new ScanResult(packs, problems);
    }

    private static void scanFolder(Path folder, List<PackSource> packs, List<PackProblem> problems) throws IOException {
        String name = folder.getFileName().toString();
        Path mcmeta = folder.resolve("pack.mcmeta");
        if (!Files.isRegularFile(mcmeta)) {
            if (!name.equals(TextureMixerConstants.GENERATED_PACK_NAME)) {
                problems.add(PackProblem.error(name, "Folder has no pack.mcmeta, so Minecraft does not treat it as a resource pack"));
            }
            return;
        }
        PackMetadata metadata;
        try (InputStream in = Files.newInputStream(mcmeta)) {
            metadata = PackMetadata.parse(new String(BoundedReader.readAll(in, MAX_MCMETA_BYTES, "pack.mcmeta"), StandardCharsets.UTF_8));
        }
        if (isGenerated(name, metadata)) {
            return;
        }
        if (metadata.parseProblem() != null) {
            problems.add(PackProblem.warning(name, metadata.parseProblem()));
        }
        packs.add(new FolderPackSource(folder, metadata));
    }

    private static void scanZip(Path file, List<PackSource> packs, List<PackProblem> problems) throws IOException {
        String name = file.getFileName().toString();
        ZipPackSource zip = ZipPackSource.open(file);
        boolean keep = false;
        try {
            if (!zip.exists("pack.mcmeta")) {
                String nested = zip.nestedRootFolder();
                problems.add(PackProblem.error(name, nested != null
                        ? "pack.mcmeta is inside the folder \"" + nested + "\" in the ZIP. Minecraft only loads packs whose pack.mcmeta is at the top level of the ZIP; re-zip the contents of that folder."
                        : "ZIP has no pack.mcmeta at its top level, so it is not a resource pack"));
                return;
            }
            PackMetadata metadata;
            try (InputStream in = zip.open("pack.mcmeta")) {
                metadata = PackMetadata.parse(new String(BoundedReader.readAll(in, MAX_MCMETA_BYTES, "pack.mcmeta"), StandardCharsets.UTF_8));
            }
            zip.setMetadata(metadata);
            if (isGenerated(name, metadata)) {
                return;
            }
            if (metadata.parseProblem() != null) {
                problems.add(PackProblem.warning(name, metadata.parseProblem()));
            }
            if (zip.unsafeEntries() > 0) {
                problems.add(PackProblem.warning(name, zip.unsafeEntries() + " ZIP entr" + (zip.unsafeEntries() == 1 ? "y has" : "ies have")
                        + " an unsafe path (absolute or containing \"..\") and " + (zip.unsafeEntries() == 1 ? "was" : "were") + " ignored"));
            }
            packs.add(zip);
            keep = true;
        } finally {
            if (!keep) {
                zip.close();
            }
        }
    }

    private static boolean isGenerated(String name, PackMetadata metadata) {
        return metadata.generatedByTextureMixer() || name.equals(TextureMixerConstants.GENERATED_PACK_NAME);
    }

    /**
     * Cheap summary of the directory's state. If it is unchanged since the last scan,
     * the previous index can be reused instead of rescanning every pack.
     */
    public static String fingerprint(Path directory) {
        if (!Files.isDirectory(directory)) {
            return "missing";
        }
        StringBuilder out = new StringBuilder();
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            stream.forEach(entries::add);
        } catch (IOException e) {
            return "unreadable:" + System.nanoTime();
        }
        entries.sort(Comparator.comparing(Path::toString));
        for (Path entry : entries) {
            String name = entry.getFileName().toString();
            if (name.equals(TextureMixerConstants.GENERATED_PACK_NAME) || name.startsWith(".")) {
                continue;
            }
            try {
                if (Files.isDirectory(entry)) {
                    out.append(new FolderPackSource(entry, PackMetadata.NONE).fingerprint());
                } else {
                    out.append(name).append(':').append(Files.size(entry)).append(':')
                            .append(Files.getLastModifiedTime(entry).toMillis());
                }
            } catch (IOException e) {
                out.append(name).append(":?");
            }
            out.append('|');
        }
        return out.toString();
    }
}
