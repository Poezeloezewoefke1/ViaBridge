package dev.texturemixer.core.session;

import dev.texturemixer.core.TextureMixerConstants;
import dev.texturemixer.core.pack.PackKind;
import dev.texturemixer.core.pack.PackSource;
import dev.texturemixer.core.profile.ProfileStore;
import dev.texturemixer.core.selection.SelectionHistory;
import dev.texturemixer.core.selection.Selections;
import dev.texturemixer.core.texture.TextureCategory;
import dev.texturemixer.core.texture.TextureEntry;
import dev.texturemixer.core.texture.TextureId;
import dev.texturemixer.core.texture.TextureIndex;
import dev.texturemixer.core.texture.TextureVariant;
import dev.texturemixer.core.util.BackgroundExecutor;
import dev.texturemixer.core.util.Log;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Everything the texture selection screen shows and edits, independent of how it is
 * drawn: the filtered texture list, the current profile's selections, undo/redo and
 * profile management. The screen only renders this state and forwards input to it,
 * which keeps the Minecraft-version-specific code small.
 *
 * <p>Not thread-safe; used from the render thread only. Saving happens in the background.
 */
public final class MixerSession {
    private final ProfileStore profiles;
    private final SelectionHistory history = new SelectionHistory();
    private final Random random;

    private TextureIndex index;
    /** Lower-case "id + pack names" per texture, built once per index so typing stays cheap. */
    private Map<TextureId, String> searchText = Map.of();
    private Selections selections;
    private Selections lastApplied;
    private List<String> enabledPackIds = List.of();

    private String query = "";
    private TextureCategory category;
    private boolean onlyChanged;
    private boolean includeUnmodded;
    private List<TextureEntry> visible = List.of();
    private TextureId focused;
    private int revision;

    public MixerSession(ProfileStore profiles, Random random) {
        this.profiles = profiles;
        this.random = random;
        this.selections = profiles.activeSelections();
    }

    // ---------------------------------------------------------------- index

    public void setIndex(TextureIndex index) {
        this.index = index;
        Map<TextureId, String> text = new HashMap<>();
        if (index != null) {
            for (TextureEntry entry : index.entries()) {
                StringBuilder builder = new StringBuilder(entry.id().toString());
                for (TextureVariant variant : entry.variants()) {
                    builder.append('\n').append(index.sourceName(variant.sourceId()).toLowerCase(Locale.ROOT));
                }
                text.put(entry.id(), builder.toString());
            }
        }
        this.searchText = text;
        refilter();
    }

    public TextureIndex index() {
        return index;
    }

    /**
     * Minecraft's currently enabled packs, lowest priority first. Used to show which
     * version of a texture the player sees today for textures they have not picked.
     */
    public void setEnabledPackIds(List<String> ids) {
        List<String> copy = new ArrayList<>(ids);
        copy.remove(TextureMixerConstants.GENERATED_PACK_ID);
        this.enabledPackIds = Collections.unmodifiableList(copy);
        revision++;
    }

    public List<String> enabledPackIds() {
        return enabledPackIds;
    }

    // ---------------------------------------------------------------- filtering

    public void setQuery(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (!normalized.equals(this.query)) {
            this.query = normalized;
            refilter();
        }
    }

    public String query() {
        return query;
    }

    /** {@code null} shows every category. */
    public void setCategory(TextureCategory category) {
        if (this.category != category) {
            this.category = category;
            refilter();
        }
    }

    public TextureCategory category() {
        return category;
    }

    public void setOnlyChanged(boolean onlyChanged) {
        if (this.onlyChanged != onlyChanged) {
            this.onlyChanged = onlyChanged;
            refilter();
        }
    }

    public boolean onlyChanged() {
        return onlyChanged;
    }

    /** Whether to also list textures that have only one version (normally hidden). */
    public void setIncludeUnmodded(boolean includeUnmodded) {
        if (this.includeUnmodded != includeUnmodded) {
            this.includeUnmodded = includeUnmodded;
            refilter();
        }
    }

    public boolean includeUnmodded() {
        return includeUnmodded;
    }

    public List<TextureEntry> visible() {
        return visible;
    }

    /** Number of listed textures per category with the current search applied. */
    public Map<TextureCategory, Integer> categoryCounts() {
        Map<TextureCategory, Integer> counts = new HashMap<>();
        if (index == null) {
            return counts;
        }
        String[] terms = terms();
        for (TextureEntry entry : index.entries()) {
            if (matchesBase(entry, terms)) {
                counts.merge(entry.category(), 1, Integer::sum);
            }
        }
        return counts;
    }

    private void refilter() {
        if (index == null) {
            visible = List.of();
        } else {
            String[] terms = terms();
            List<TextureEntry> result = new ArrayList<>();
            for (TextureEntry entry : index.entries()) {
                if ((category == null || entry.category() == category) && matchesBase(entry, terms)) {
                    result.add(entry);
                }
            }
            visible = Collections.unmodifiableList(result);
        }
        revision++;
    }

    private String[] terms() {
        return query.isEmpty() ? new String[0] : query.split("\\s+");
    }

    private boolean matchesBase(TextureEntry entry, String[] terms) {
        if (!includeUnmodded && !worthListing(entry) && selections.get(entry.id()) == null) {
            return false;
        }
        if (onlyChanged && selections.get(entry.id()) == null) {
            return false;
        }
        String text = searchText.getOrDefault(entry.id(), entry.id().toString());
        for (String term : terms) {
            if (!text.contains(term)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the default view lists a texture: it has more than one version to choose
     * from, or a pack in the resourcepacks folder has it. Textures only one built-in or
     * mod pack provides would otherwise flood the list.
     */
    private boolean worthListing(TextureEntry entry) {
        if (entry.variants().size() > 1) {
            return true;
        }
        for (TextureVariant variant : entry.variants()) {
            PackSource source = index.source(variant.sourceId());
            if (source != null && source.kind() != PackKind.BUILTIN) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- focus

    public void focus(TextureId id) {
        this.focused = id;
        revision++;
    }

    public TextureEntry focusedEntry() {
        return focused == null || index == null ? null : index.get(focused);
    }

    // ---------------------------------------------------------------- selections

    public Selections selections() {
        return selections;
    }

    /** Source picked for a texture, or {@code null} when it is left unchanged. */
    public String choice(TextureId id) {
        return selections.get(id);
    }

    /**
     * The source whose version the player currently sees in game for an unpicked
     * texture: the highest-priority enabled pack that has it, else Minecraft's own.
     */
    public String currentSource(TextureEntry entry) {
        for (int i = enabledPackIds.size() - 1; i >= 0; i--) {
            if (entry.variant(enabledPackIds.get(i)) != null) {
                return enabledPackIds.get(i);
            }
        }
        return entry.vanilla() != null ? TextureMixerConstants.VANILLA_SOURCE_ID : null;
    }

    /** The source shown for a texture after applying: the picked one, else the current one. */
    public String effectiveSource(TextureEntry entry) {
        String chosen = selections.get(entry.id());
        return chosen != null && entry.variant(chosen) != null ? chosen : currentSource(entry);
    }

    /** Picks {@code sourceId} for a texture; {@code null} returns it to "unchanged". */
    public void choose(TextureId id, String sourceId) {
        update(sourceId == null ? selections.without(id) : selections.with(id, sourceId));
    }

    /**
     * Picks {@code sourceId} for every listed texture that it has a usable version of, or
     * returns them all to "unchanged" when {@code sourceId} is {@code null}. One undo step.
     *
     * @return number of textures changed
     */
    public int chooseForVisible(String sourceId) {
        Map<TextureId, String> choices = new HashMap<>(selections.asMap());
        int changed = 0;
        for (TextureEntry entry : visible) {
            if (sourceId == null) {
                if (choices.remove(entry.id()) != null) {
                    changed++;
                }
                continue;
            }
            TextureVariant variant = entry.variant(sourceId);
            if (variant != null && !variant.legacyPath() && !sourceId.equals(choices.put(entry.id(), sourceId))) {
                changed++;
            }
        }
        update(Selections.of(choices));
        return changed;
    }

    /** How many listed textures {@link #chooseForVisible} would apply {@code sourceId} to. */
    public int countVisibleWith(String sourceId) {
        if (sourceId == null) {
            return visible.size();
        }
        int count = 0;
        for (TextureEntry entry : visible) {
            TextureVariant variant = entry.variant(sourceId);
            if (variant != null && !variant.legacyPath()) {
                count++;
            }
        }
        return count;
    }

    public boolean canUndo() {
        return history.canUndo();
    }

    public boolean canRedo() {
        return history.canRedo();
    }

    public void undo() {
        Selections previous = history.undo(selections);
        if (previous != null) {
            selections = previous;
            afterChange();
        }
    }

    public void redo() {
        Selections next = history.redo(selections);
        if (next != null) {
            selections = next;
            afterChange();
        }
    }

    /** Clears every selection in the current profile (undoable). */
    public void resetAll() {
        update(Selections.EMPTY);
    }

    /**
     * Picks a random version for every listed texture that has more than one (undoable,
     * as a single step). Versions stored under outdated folder names are not used.
     *
     * @return number of textures changed
     */
    public int randomizeVisible() {
        Map<TextureId, String> choices = new HashMap<>(selections.asMap());
        int changed = 0;
        for (TextureEntry entry : visible) {
            List<String> options = new ArrayList<>();
            for (TextureVariant variant : entry.variants()) {
                if (!variant.legacyPath()) {
                    options.add(variant.sourceId());
                }
            }
            if (options.size() < 2) {
                continue;
            }
            String pick = options.get(random.nextInt(options.size()));
            if (!pick.equals(choices.put(entry.id(), pick))) {
                changed++;
            }
        }
        update(Selections.of(choices));
        return changed;
    }

    private void update(Selections next) {
        if (next.equals(selections)) {
            return;
        }
        history.record(selections);
        selections = next;
        afterChange();
    }

    private void afterChange() {
        profiles.put(profiles.active(), selections);
        if (onlyChanged || !includeUnmodded) {
            // Both views depend on which textures are picked.
            refilter();
        }
        revision++;
        saveAsync();
    }

    // ---------------------------------------------------------------- applying

    /** Whether the selections differ from what was last applied in this session. */
    public boolean hasUnappliedChanges() {
        return lastApplied == null ? !selections.isEmpty() : !lastApplied.equals(selections);
    }

    public void markApplied(Selections applied) {
        lastApplied = applied;
        revision++;
    }

    // ---------------------------------------------------------------- profiles

    public ProfileStore profiles() {
        return profiles;
    }

    public String activeProfile() {
        return profiles.active();
    }

    public void switchProfile(String name) {
        if (name.equals(profiles.active())) {
            return;
        }
        profiles.put(profiles.active(), selections);
        profiles.setActive(name);
        selections = profiles.activeSelections();
        history.clear();
        refilter();
        saveAsync();
    }

    /** Creates a profile, optionally copying the current selections, and switches to it. */
    public String createProfile(String name, boolean copyCurrent) {
        profiles.put(profiles.active(), selections);
        String error = profiles.create(name, copyCurrent ? selections : Selections.EMPTY);
        if (error == null) {
            switchProfile(name.trim());
        }
        return error;
    }

    public String renameProfile(String oldName, String newName) {
        String error = profiles.rename(oldName, newName);
        if (error == null) {
            revision++;
            saveAsync();
        }
        return error;
    }

    public String deleteProfile(String name) {
        boolean wasActive = name.equals(profiles.active());
        String error = profiles.delete(name);
        if (error == null) {
            if (wasActive) {
                selections = profiles.activeSelections();
                history.clear();
                refilter();
            }
            revision++;
            saveAsync();
        }
        return error;
    }

    /** Saves in the background; the JSON snapshot is taken now, on the calling thread. */
    public void saveAsync() {
        profiles.put(profiles.active(), selections);
        Path file = profiles.file();
        String json = profiles.toJson();
        BackgroundExecutor.SAVE.execute(() -> {
            try {
                ProfileStore.writeAtomically(file, json);
            } catch (IOException e) {
                Log.LOGGER.error("Could not save texture profiles to {}", file, e);
            }
        });
    }

    /** Increases whenever anything visible changes; lets the screen skip needless rebuilds. */
    public int revision() {
        return revision;
    }
}
