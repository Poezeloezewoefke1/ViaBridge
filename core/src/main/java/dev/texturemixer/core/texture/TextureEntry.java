package dev.texturemixer.core.texture;

import java.util.List;

/**
 * One texture and every version of it that was found, ordered with Minecraft's own
 * version first and then the packs in folder order.
 */
public record TextureEntry(TextureId id, TextureCategory category, List<TextureVariant> variants) {
    public TextureVariant variant(String sourceId) {
        for (TextureVariant variant : variants) {
            if (variant.sourceId().equals(sourceId)) {
                return variant;
            }
        }
        return null;
    }

    public TextureVariant vanilla() {
        for (TextureVariant variant : variants) {
            if (variant.isVanilla()) {
                return variant;
            }
        }
        return null;
    }

    /** Number of packs (not counting Minecraft itself) that provide this texture. */
    public int packCount() {
        int count = 0;
        for (TextureVariant variant : variants) {
            if (!variant.isVanilla()) {
                count++;
            }
        }
        return count;
    }
}
