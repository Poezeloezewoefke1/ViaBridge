package dev.texturemixer.fabric.compat;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The few calls whose names or signatures differ between Minecraft versions, implemented
 * for <b>Minecraft 1.20.2 - 1.20.4</b>. Every version module has a class with this exact name and
 * these exact members; the shared code in {@code fabric/common} only calls through here.
 */
public final class VersionCompat {
    private VersionCompat() {
    }

    /** Resource pack format of the running game. */
    public static int packFormat() {
        return SharedConstants.getGameVersion().getResourceVersion(ResourceType.CLIENT_RESOURCES);
    }

    public static Identifier id(String namespace, String path) {
        return new Identifier(namespace, path);
    }

    /** Draws a {@code regionWidth}x{@code regionHeight} region from the top-left of a texture, scaled to the given size. */
    public static void drawTexture(DrawContext context, Identifier texture, int x, int y, int width, int height,
                                   int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        context.drawTexture(texture, x, y, width, height, 0.0f, 0.0f, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    /** Since 1.20.2 Minecraft draws screen backgrounds itself; drawing it twice would trip the blur check added in 1.20.5. */
    public static void renderBackgroundIfNeeded(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
    }

    /** Enabled pack ids, lowest priority first. */
    public static List<String> enabledPackIds(ResourcePackManager manager) {
        return new ArrayList<>(manager.getEnabledNames());
    }

    /** Id of a pack profile, as stored in options.txt. */
    public static String profileId(ResourcePackProfile profile) {
        return profile.getName();
    }

    /** Uploads an image as a texture (takes ownership of the image). Render thread only. */
    public static void registerDynamicTexture(TextureManager textureManager, Identifier id, NativeImage image) {
        textureManager.registerTexture(id, new NativeImageBackedTexture(image));
    }
}
