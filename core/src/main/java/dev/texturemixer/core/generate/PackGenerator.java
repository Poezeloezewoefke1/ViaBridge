package dev.texturemixer.core.generate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.texturemixer.core.TextureMixerConstants;
import dev.texturemixer.core.pack.PackSource;
import dev.texturemixer.core.selection.Selections;
import dev.texturemixer.core.texture.PngInfo;
import dev.texturemixer.core.texture.TextureEntry;
import dev.texturemixer.core.texture.TextureId;
import dev.texturemixer.core.texture.TextureIndex;
import dev.texturemixer.core.texture.TextureVariant;
import dev.texturemixer.core.util.BoundedReader;
import dev.texturemixer.core.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds a resource pack from a set of selections: every chosen texture is copied from
 * its source pack to the same resource location, together with its {@code .png.mcmeta}
 * (animation, blur/clamp settings) when the source has one.
 *
 * <p>Source packs are only ever read. Textures that cannot be copied (pack removed,
 * corrupt file, ...) are skipped and reported; one bad texture never fails the whole pack.
 */
public final class PackGenerator {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long MAX_MCMETA_BYTES = 1024 * 1024;

    private PackGenerator() {
    }

    public static GenerationResult generate(Selections selections, TextureIndex index, PackWriter writer,
                                            GenerationOptions options) throws IOException {
        List<String> skipped = new ArrayList<>();
        Map<String, String> credits = new TreeMap<>();
        int written = 0;

        for (Map.Entry<TextureId, String> choice : selections.asMap().entrySet()) {
            TextureId id = choice.getKey();
            String sourceId = choice.getValue();
            PackSource source = index.source(sourceId);
            if (source == null) {
                skipped.add(id + ": pack \"" + displayId(sourceId) + "\" is no longer installed");
                continue;
            }
            TextureEntry entry = index.get(id);
            TextureVariant variant = entry != null ? entry.variant(sourceId) : null;
            String pngPath = variant != null ? variant.filePath() : id.assetPath();
            String metadataPath = variant != null ? variant.metadataPath()
                    : (source.exists(id.metadataPath()) ? id.metadataPath() : null);

            byte[] png;
            try (InputStream in = source.open(pngPath)) {
                png = BoundedReader.readAll(in, TextureMixerConstants.MAX_ASSET_BYTES, pngPath);
            } catch (IOException e) {
                skipped.add(id + ": could not be read from \"" + source.displayName() + "\" (" + e.getMessage() + ")");
                continue;
            }
            if (PngInfo.parse(png) == null) {
                skipped.add(id + ": the file in \"" + source.displayName() + "\" is not a valid PNG");
                continue;
            }

            byte[] metadata = null;
            if (metadataPath != null) {
                try (InputStream in = source.open(metadataPath)) {
                    metadata = BoundedReader.readAll(in, MAX_MCMETA_BYTES, metadataPath);
                    JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (IOException | RuntimeException e) {
                    // A broken .mcmeta makes Minecraft show the missing texture, so leave it out.
                    metadata = null;
                    skipped.add(id + ": animation/metadata file in \"" + source.displayName() + "\" is invalid and was left out");
                }
            }

            writer.write(id.assetPath(), png);
            if (metadata != null) {
                writer.write(id.metadataPath(), metadata);
            }
            credits.put(id.toString(), source.displayName());
            written++;
        }

        writer.write("pack.mcmeta", packMcmeta(options, written, selections.digest()).getBytes(StandardCharsets.UTF_8));
        writer.write("texturemixer-sources.json", sourcesJson(options, credits).getBytes(StandardCharsets.UTF_8));
        if (options.icon() != null) {
            writer.write("pack.png", options.icon());
        }
        writer.commit();

        Log.LOGGER.info("Generated pack at {} with {} texture(s), {} skipped", writer.describeTarget(), written, skipped.size());
        for (String reason : skipped) {
            Log.LOGGER.warn("Skipped {}", reason);
        }
        return new GenerationResult(written, List.copyOf(skipped), writer.describeTarget());
    }

    static String packMcmeta(GenerationOptions options, int textureCount, String selectionsDigest) {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", options.packFormat());
        if (options.packFormat() >= GenerationOptions.MIN_MAX_FORMAT_SINCE) {
            pack.addProperty("min_format", options.packFormat());
            pack.addProperty("max_format", options.packFormat());
        }
        pack.addProperty("description", "Texture Mixer: " + options.profileName() + " (" + textureCount + " texture" + (textureCount == 1 ? "" : "s") + ")");

        JsonObject marker = new JsonObject();
        marker.addProperty("generated", true);
        marker.addProperty("profile", options.profileName());
        marker.addProperty("game_version", options.gameVersion());
        marker.addProperty("selections", selectionsDigest);

        JsonObject root = new JsonObject();
        root.add("pack", pack);
        root.add(TextureMixerConstants.MARKER_KEY, marker);
        return GSON.toJson(root);
    }

    /**
     * Reads which profile and selections a generated pack was built from, or
     * {@code null} if the folder is not a pack generated by Texture Mixer.
     */
    public static GeneratedInfo readGeneratedInfo(java.nio.file.Path packFolder) {
        java.nio.file.Path mcmeta = packFolder.resolve("pack.mcmeta");
        try {
            JsonObject root = JsonParser.parseString(java.nio.file.Files.readString(mcmeta, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject marker = root.getAsJsonObject(TextureMixerConstants.MARKER_KEY);
            if (marker == null) {
                return null;
            }
            return new GeneratedInfo(
                    marker.has("profile") ? marker.get("profile").getAsString() : "",
                    marker.has("selections") ? marker.get("selections").getAsString() : "");
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Where a generated pack came from. */
    public record GeneratedInfo(String profile, String selectionsDigest) {
    }

    private static String sourcesJson(GenerationOptions options, Map<String, String> credits) {
        JsonObject root = new JsonObject();
        root.addProperty("note", "Textures in this pack were combined by Texture Mixer from the packs listed below. They remain the work of those packs' authors; check each pack's license before sharing.");
        root.addProperty("profile", options.profileName());
        JsonObject textures = new JsonObject();
        credits.forEach(textures::addProperty);
        root.add("textures", textures);
        return GSON.toJson(root);
    }

    private static String displayId(String sourceId) {
        return sourceId.startsWith("file/") ? sourceId.substring("file/".length()) : sourceId;
    }
}
