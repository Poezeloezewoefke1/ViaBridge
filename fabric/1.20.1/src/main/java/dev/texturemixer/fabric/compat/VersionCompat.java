package dev.texturemixer.fabric.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The few calls whose names or signatures differ between Minecraft versions, implemented
 * for <b>Minecraft 1.20.1</b>. See the 1.21.4 version for the contract.
 */
public final class VersionCompat {
    /** Resource pack format of Minecraft 1.20.1. */
    public static final int PACK_FORMAT = 15;

    private VersionCompat() {
    }

    public static Identifier id(String namespace, String path) {
        return new Identifier(namespace, path);
    }

    public static void drawTexture(DrawContext context, Identifier texture, int x, int y, int width, int height,
                                   int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        context.drawTexture(texture, x, y, width, height, 0.0f, 0.0f, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    /** Before 1.20.2 screens draw their own background. */
    public static void renderBackgroundIfNeeded(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        screen.renderBackground(context);
    }

    public static List<String> enabledPackIds(ResourcePackManager manager) {
        return new ArrayList<>(manager.getEnabledNames());
    }

    public static void registerDynamicTexture(TextureManager textureManager, Identifier id, NativeImage image) {
        textureManager.registerTexture(id, new NativeImageBackedTexture(image));
    }
}
