package dev.texturemixer.core.selection;

import dev.texturemixer.core.texture.TextureId;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Immutable set of choices: for each texture the player picked, the id of the source
 * whose version should be used ({@code vanilla} forces Minecraft's own texture).
 * Textures without an entry are left alone, so whatever the player's other enabled
 * packs (or Minecraft) provide keeps showing.
 *
 * <p>Immutability is what makes undo/redo trivial: history is just a list of these.
 */
public final class Selections {
    public static final Selections EMPTY = new Selections(new TreeMap<>());

    private final SortedMap<TextureId, String> choices;

    private Selections(SortedMap<TextureId, String> choices) {
        this.choices = Collections.unmodifiableSortedMap(choices);
    }

    public static Selections of(Map<TextureId, String> choices) {
        return choices.isEmpty() ? EMPTY : new Selections(new TreeMap<>(choices));
    }

    /** Chosen source for a texture, or {@code null} if the texture is left unchanged. */
    public String get(TextureId id) {
        return choices.get(id);
    }

    public Selections with(TextureId id, String sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        if (sourceId.equals(choices.get(id))) {
            return this;
        }
        TreeMap<TextureId, String> copy = new TreeMap<>(choices);
        copy.put(id, sourceId);
        return new Selections(copy);
    }

    public Selections without(TextureId id) {
        if (!choices.containsKey(id)) {
            return this;
        }
        TreeMap<TextureId, String> copy = new TreeMap<>(choices);
        copy.remove(id);
        return copy.isEmpty() ? EMPTY : new Selections(copy);
    }

    public Map<TextureId, String> asMap() {
        return choices;
    }

    public int size() {
        return choices.size();
    }

    public boolean isEmpty() {
        return choices.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Selections that && choices.equals(that.choices);
    }

    @Override
    public int hashCode() {
        return choices.hashCode();
    }

    @Override
    public String toString() {
        return "Selections" + choices;
    }
}
