package dev.texturemixer.core.texture;

/**
 * Groups shown as filter tabs. Classification is by path, and accepts both the current
 * folder names and the pre-1.13 ones ({@code blocks/}, {@code items/}, {@code models/armor/})
 * so that older packs still land in the right tab.
 */
public enum TextureCategory {
    BLOCKS("blocks"),
    ITEMS("items"),
    ENTITIES("entities"),
    ARMOR("armor"),
    GUI("gui"),
    PARTICLES("particles"),
    OTHER("other");

    private final String key;

    TextureCategory(String key) {
        this.key = key;
    }

    /** Suffix of the translation key, {@code texturemixer.category.<key>}. */
    public String key() {
        return key;
    }

    public static TextureCategory classify(TextureId id) {
        String path = id.path();
        if (path.startsWith("block/") || path.startsWith("blocks/")) {
            return BLOCKS;
        }
        if (path.startsWith("item/") || path.startsWith("items/")) {
            return ITEMS;
        }
        if (path.startsWith("entity/equipment/") || path.startsWith("models/armor/")
                || path.startsWith("trims/models/armor/") || path.startsWith("trims/entity/")) {
            return ARMOR;
        }
        if (path.startsWith("entity/") || path.startsWith("mob/")) {
            return ENTITIES;
        }
        if (path.startsWith("gui/") || path.startsWith("mob_effect/") || path.startsWith("font/")) {
            return GUI;
        }
        if (path.startsWith("particle/") || path.startsWith("particles/")) {
            return PARTICLES;
        }
        return OTHER;
    }

    /** Texture folders that only existed before Minecraft 1.13 (pack format 4). */
    public static boolean isLegacyPath(TextureId id) {
        String path = id.path();
        return path.startsWith("blocks/") || path.startsWith("items/");
    }
}
