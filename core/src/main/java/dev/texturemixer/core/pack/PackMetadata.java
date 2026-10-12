package dev.texturemixer.core.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.texturemixer.core.TextureMixerConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The parts of {@code pack.mcmeta} Texture Mixer cares about.
 *
 * <p>Parsing is deliberately lenient: Minecraft has changed this file several times
 * (plain {@code pack_format}, {@code supported_formats} in 1.20.2, overlays in 1.20.2,
 * {@code min_format}/{@code max_format} in 1.21.9), and a pack with an odd file should
 * still have its textures offered, with a warning.
 */
public final class PackMetadata {
    public static final PackMetadata NONE = new PackMetadata(-1, "", null, List.of(), false, null);

    private final int packFormat;
    private final String description;
    private final FormatRange supportedFormats;
    private final List<Overlay> overlays;
    private final boolean generatedByTextureMixer;
    private final String parseProblem;

    private PackMetadata(int packFormat, String description, FormatRange supportedFormats,
                         List<Overlay> overlays, boolean generatedByTextureMixer, String parseProblem) {
        this.packFormat = packFormat;
        this.description = description;
        this.supportedFormats = supportedFormats;
        this.overlays = overlays;
        this.generatedByTextureMixer = generatedByTextureMixer;
        this.parseProblem = parseProblem;
    }

    /** Declared {@code pack_format} (or {@code min_format} major), -1 if absent. */
    public int packFormat() {
        return packFormat;
    }

    public String description() {
        return description;
    }

    public List<Overlay> overlays() {
        return overlays;
    }

    public boolean generatedByTextureMixer() {
        return generatedByTextureMixer;
    }

    /** Human-readable problem found while parsing, or {@code null}. */
    public String parseProblem() {
        return parseProblem;
    }

    /** Whether this pack declares support for {@code format}. Unknown declarations count as "no". */
    public boolean supports(int format) {
        if (supportedFormats != null) {
            return supportedFormats.contains(format);
        }
        return packFormat == format;
    }

    /** Overlays that apply for the given format, in the order Minecraft stacks them. */
    public List<Overlay> activeOverlays(int format) {
        List<Overlay> active = new ArrayList<>();
        for (Overlay overlay : overlays) {
            if (overlay.formats().contains(format)) {
                active.add(overlay);
            }
        }
        return active;
    }

    public static PackMetadata parse(String json) {
        JsonObject root;
        try {
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                return withProblem("pack.mcmeta is not a JSON object");
            }
            root = element.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            return withProblem("pack.mcmeta is not valid JSON (" + firstLine(e.getMessage()) + ")");
        }

        boolean generated = root.has(TextureMixerConstants.MARKER_KEY);
        if (!root.has("pack") || !root.get("pack").isJsonObject()) {
            return new PackMetadata(-1, "", null, List.of(), generated, "pack.mcmeta has no \"pack\" section");
        }
        JsonObject pack = root.getAsJsonObject("pack");

        String problem = null;
        int format = -1;
        FormatRange supported = null;
        try {
            if (pack.has("pack_format")) {
                format = pack.get("pack_format").getAsInt();
            }
            if (pack.has("supported_formats")) {
                supported = FormatRange.parse(pack.get("supported_formats"));
            }
            if (pack.has("min_format") || pack.has("max_format")) {
                int min = pack.has("min_format") ? majorVersion(pack.get("min_format")) : format;
                int max = pack.has("max_format") ? majorVersion(pack.get("max_format")) : Integer.MAX_VALUE;
                supported = new FormatRange(min, max);
                if (format < 0) {
                    format = min;
                }
            }
            if (format < 0 && supported == null) {
                problem = "pack.mcmeta does not declare a pack format";
            }
        } catch (RuntimeException e) {
            problem = "pack.mcmeta has an unreadable pack format";
        }
        if (supported == null && format >= 0) {
            supported = new FormatRange(format, format);
        }

        List<Overlay> overlays = new ArrayList<>();
        try {
            if (root.has("overlays") && root.get("overlays").isJsonObject()) {
                JsonArray entries = root.getAsJsonObject("overlays").getAsJsonArray("entries");
                if (entries != null) {
                    for (JsonElement entry : entries) {
                        JsonObject object = entry.getAsJsonObject();
                        String directory = SafePaths.normalize(object.get("directory").getAsString());
                        FormatRange range;
                        if (object.has("formats")) {
                            range = FormatRange.parse(object.get("formats"));
                        } else {
                            range = new FormatRange(
                                    object.has("min_format") ? majorVersion(object.get("min_format")) : 0,
                                    object.has("max_format") ? majorVersion(object.get("max_format")) : Integer.MAX_VALUE);
                        }
                        if (directory != null && !directory.contains("/")) {
                            overlays.add(new Overlay(directory, range));
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            problem = problem == null ? "pack.mcmeta has malformed overlay entries; overlays were ignored" : problem;
            overlays.clear();
        }

        return new PackMetadata(format, describe(pack.get("description")), supported,
                Collections.unmodifiableList(overlays), generated, problem);
    }

    private static PackMetadata withProblem(String problem) {
        return new PackMetadata(-1, "", null, List.of(), false, problem);
    }

    private static int majorVersion(JsonElement element) {
        if (element.isJsonArray()) {
            return element.getAsJsonArray().get(0).getAsInt();
        }
        return element.getAsInt();
    }

    /** Flattens a JSON text component into plain text (good enough for a label). */
    static String describe(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        StringBuilder out = new StringBuilder();
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                out.append(describe(child));
            }
            return out.toString();
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("text")) {
            out.append(describe(object.get("text")));
        } else if (object.has("translate")) {
            out.append(describe(object.get("fallback") != null ? object.get("fallback") : object.get("translate")));
        }
        if (object.has("extra")) {
            out.append(describe(object.get("extra")));
        }
        return out.toString();
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }

    /** Inclusive range of pack formats. */
    public record FormatRange(int min, int max) {
        public boolean contains(int format) {
            return format >= min && format <= max;
        }

        static FormatRange parse(JsonElement element) {
            if (element.isJsonPrimitive()) {
                int value = element.getAsInt();
                return new FormatRange(value, value);
            }
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                int min = array.get(0).getAsInt();
                int max = array.size() > 1 ? array.get(1).getAsInt() : min;
                return new FormatRange(min, max);
            }
            JsonObject object = element.getAsJsonObject();
            return new FormatRange(object.get("min_inclusive").getAsInt(), object.get("max_inclusive").getAsInt());
        }
    }

    /** A sub-directory whose files replace the base pack's files for some formats. */
    public record Overlay(String directory, FormatRange formats) {
    }
}
