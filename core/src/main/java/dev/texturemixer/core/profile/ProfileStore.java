package dev.texturemixer.core.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.texturemixer.core.selection.Selections;
import dev.texturemixer.core.texture.TextureId;
import dev.texturemixer.core.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Named texture profiles, persisted as JSON in the config folder.
 *
 * <pre>{@code
 * {
 *   "format": 1,
 *   "active": "Default",
 *   "profiles": [
 *     { "name": "Default", "selections": { "minecraft:block/stone": "file/Some Pack.zip" } }
 *   ]
 * }
 * }</pre>
 *
 * Writes go to a temporary file that is then moved over the real one, so a crash mid-save
 * cannot leave a half-written file. An unreadable file is moved aside (never deleted)
 * and Texture Mixer starts with a fresh default profile.
 */
public final class ProfileStore {
    public static final String DEFAULT_PROFILE = "Default";
    public static final int MAX_NAME_LENGTH = 32;
    private static final int FORMAT = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private final LinkedHashMap<String, Selections> profiles = new LinkedHashMap<>();
    private String active = DEFAULT_PROFILE;
    private String loadProblem;

    private ProfileStore(Path file) {
        this.file = file;
    }

    public static ProfileStore load(Path file) {
        ProfileStore store = new ProfileStore(file);
        if (Files.exists(file)) {
            try {
                store.read(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                Path backup = file.resolveSibling(file.getFileName() + ".corrupt-"
                        + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
                store.profiles.clear();
                store.loadProblem = "Saved profiles could not be read and were moved to " + backup.getFileName();
                Log.LOGGER.error("Could not read {}; moving it to {}", file, backup, e);
                try {
                    Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveFailure) {
                    Log.LOGGER.error("Could not move unreadable profile file aside", moveFailure);
                }
            }
        }
        if (store.profiles.isEmpty()) {
            store.profiles.put(DEFAULT_PROFILE, Selections.EMPTY);
        }
        if (!store.profiles.containsKey(store.active)) {
            store.active = store.profiles.keySet().iterator().next();
        }
        return store;
    }

    private void read(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray list = root.getAsJsonArray("profiles");
        int skipped = 0;
        for (JsonElement element : list) {
            JsonObject profile = element.getAsJsonObject();
            String name = profile.get("name").getAsString();
            TreeMap<TextureId, String> choices = new TreeMap<>();
            JsonObject selections = profile.has("selections") ? profile.getAsJsonObject("selections") : new JsonObject();
            for (Map.Entry<String, JsonElement> choice : selections.entrySet()) {
                try {
                    choices.put(TextureId.parse(choice.getKey()), choice.getValue().getAsString());
                } catch (RuntimeException e) {
                    skipped++;
                }
            }
            if (validateName(name, null) == null) {
                profiles.put(name, Selections.of(choices));
            }
        }
        if (skipped > 0) {
            Log.LOGGER.warn("Ignored {} invalid texture selection(s) in {}", skipped, file);
        }
        if (root.has("active")) {
            active = root.get("active").getAsString();
        }
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("active", active);
        JsonArray list = new JsonArray();
        for (Map.Entry<String, Selections> profile : profiles.entrySet()) {
            JsonObject object = new JsonObject();
            object.addProperty("name", profile.getKey());
            JsonObject selections = new JsonObject();
            for (Map.Entry<TextureId, String> choice : profile.getValue().asMap().entrySet()) {
                selections.addProperty(choice.getKey().toString(), choice.getValue());
            }
            object.add("selections", selections);
            list.add(object);
        }
        root.add("profiles", list);
        return GSON.toJson(root);
    }

    /** Writes the current state. Safe to call from a background thread with a JSON snapshot. */
    public static void writeAtomically(Path file, String json) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, json, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void save() throws IOException {
        writeAtomically(file, toJson());
    }

    public Path file() {
        return file;
    }

    /** Problem found while loading, to show once in the GUI; {@code null} if none. */
    public String loadProblem() {
        return loadProblem;
    }

    public List<String> names() {
        return new ArrayList<>(profiles.keySet());
    }

    public String active() {
        return active;
    }

    public void setActive(String name) {
        if (!profiles.containsKey(name)) {
            throw new IllegalArgumentException("No profile named " + name);
        }
        active = name;
    }

    public Selections get(String name) {
        return profiles.getOrDefault(name, Selections.EMPTY);
    }

    public Selections activeSelections() {
        return get(active);
    }

    public void put(String name, Selections selections) {
        if (!profiles.containsKey(name)) {
            throw new IllegalArgumentException("No profile named " + name);
        }
        profiles.put(name, selections);
    }

    /** Creates a profile; returns an error message, or {@code null} on success. */
    public String create(String name, Selections initial) {
        String error = validateName(name, null);
        if (error != null) {
            return error;
        }
        profiles.put(name.trim(), initial);
        return null;
    }

    public String rename(String oldName, String newName) {
        if (!profiles.containsKey(oldName)) {
            return "No profile named " + oldName;
        }
        String error = validateName(newName, oldName);
        if (error != null) {
            return error;
        }
        String trimmed = newName.trim();
        LinkedHashMap<String, Selections> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Selections> entry : profiles.entrySet()) {
            copy.put(entry.getKey().equals(oldName) ? trimmed : entry.getKey(), entry.getValue());
        }
        profiles.clear();
        profiles.putAll(copy);
        if (active.equals(oldName)) {
            active = trimmed;
        }
        return null;
    }

    public String delete(String name) {
        if (!profiles.containsKey(name)) {
            return "No profile named " + name;
        }
        if (profiles.size() == 1) {
            return "The last profile cannot be deleted";
        }
        profiles.remove(name);
        if (active.equals(name)) {
            active = profiles.keySet().iterator().next();
        }
        return null;
    }

    /** Returns a free name based on {@code base}: "Base", "Base 2", "Base 3", ... */
    public String uniqueName(String base) {
        String trimmed = base.length() > MAX_NAME_LENGTH - 4 ? base.substring(0, MAX_NAME_LENGTH - 4) : base;
        if (validateName(trimmed, null) == null) {
            return trimmed;
        }
        for (int i = 2; ; i++) {
            String candidate = trimmed + " " + i;
            if (validateName(candidate, null) == null) {
                return candidate;
            }
        }
    }

    private String validateName(String name, String ignoring) {
        if (name == null || name.trim().isEmpty()) {
            return "Profile name cannot be empty";
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return "Profile name is longer than " + MAX_NAME_LENGTH + " characters";
        }
        for (String existing : profiles.keySet()) {
            if (!existing.equals(ignoring) && existing.toLowerCase(Locale.ROOT).equals(trimmed.toLowerCase(Locale.ROOT))) {
                return "A profile named \"" + existing + "\" already exists";
            }
        }
        return null;
    }
}
