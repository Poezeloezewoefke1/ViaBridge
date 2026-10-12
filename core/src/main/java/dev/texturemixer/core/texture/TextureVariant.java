package dev.texturemixer.core.texture;

import dev.texturemixer.core.TextureMixerConstants;

/**
 * One pack's version of a texture.
 *
 * @param id            the texture this is a version of
 * @param sourceId      id of the pack that provides it ({@code vanilla} for Minecraft's own)
 * @param filePath      where the PNG actually lives in that pack (differs from the
 *                      texture's asset path when it comes from an overlay folder)
 * @param metadataPath  path of the accompanying {@code .png.mcmeta}, or {@code null}
 * @param frameWidth    width of one animation frame (the whole width if not animated)
 * @param frameHeight   height of one animation frame (the whole height if not animated)
 * @param animated      the {@code .mcmeta} declares an animation
 * @param frameTime     ticks per animation frame (1 when not animated)
 * @param legacyPath    stored under a pre-1.13 folder name that current versions ignore
 */
public record TextureVariant(TextureId id, String sourceId, String filePath, String metadataPath,
                             int width, int height, int frameWidth, int frameHeight,
                             boolean animated, int frameTime, boolean legacyPath) {
    public boolean isVanilla() {
        return TextureMixerConstants.VANILLA_SOURCE_ID.equals(sourceId);
    }
}
