package dev.texturemixer.fabric.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The few calls whose names or signatures differ between Minecraft versions, implemented
 * for <b>Minecraft 1.21.4</b>. Every version module has a class with this exact name and
 * these exact members; the shared code in {@code fabric/common} only calls through here.
 */
public final class VersionCompat {
    /** Resource pack format of Minecraft 1.21.4. */
    public static final int PACK_FORMAT = 46;

    private VersionCompat() {
    }

    public static Identifier id(String namespace, String path) {
        return Identifier.of(namespace, path);
    }

    /** Draws a {@code regionWidth}x{@code regionHeight} region from the top-left of a texture, scaled to the given size. */
    public static void drawTexture(DrawContext context, Identifier texture, int x, int y, int width, int height,
                                   int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        context.drawTexture(RenderLayer::getGuiTextured, texture, x, y, 0.0f, 0.0f, width, height,
                regionWidth, regionHeight, textureWidth, textureHeight);
    }

    /** Since 1.20.2 {@link Screen#render} draws the background itself; drawing it twice would crash 1.21.4's blur. */
    public static void renderBackgroundIfNeeded(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
    }

    /** Enabled pack ids, lowest priority first. */
    public static List<String> enabledPackIds(ResourcePackManager manager) {
        return new ArrayList<>(manager.getEnabledIds());
    }

    /** Uploads an image as a texture (takes ownership of the image). Render thread only. */
    public static void registerDynamicTexture(TextureManager textureManager, Identifier id, NativeImage image) {
        textureManager.registerTexture(id, new NativeImageBackedTexture(image));
    }
}
