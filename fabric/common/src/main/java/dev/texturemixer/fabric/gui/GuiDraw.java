package dev.texturemixer.fabric.gui;

import dev.texturemixer.core.texture.TextureIndex;
import dev.texturemixer.core.texture.TextureVariant;
import dev.texturemixer.fabric.PreviewCache;
import dev.texturemixer.fabric.compat.VersionCompat;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Small drawing helpers shared by Texture Mixer's screens. Colours are ARGB. */
final class GuiDraw {
    static final int WHITE = 0xFFFFFFFF;
    static final int GRAY = 0xFFA0A0A0;
    static final int DARK_GRAY = 0xFF707070;
    static final int GREEN = 0xFF55FF55;
    static final int GOLD = 0xFFFFAA00;
    static final int RED = 0xFFFF5555;
    static final int YELLOW = 0xFFFFFF55;
    static final int PANEL = 0xA0000000;
    static final int HOVER = 0x30FFFFFF;
    static final int SELECTED = 0x5040A040;

    private GuiDraw() {
    }

    static void border(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + 1, color);
        context.fill(x, y + height - 1, x + width, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    /** Draws text cut to {@code maxWidth} pixels, with an ellipsis if it had to be cut. */
    static void text(DrawContext context, TextRenderer renderer, String text, int x, int y, int maxWidth, int color) {
        context.drawTextWithShadow(renderer, trim(renderer, text, maxWidth), x, y, color);
    }

    static String trim(TextRenderer renderer, String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (renderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        return renderer.trimToWidth(text, Math.max(0, maxWidth - renderer.getWidth(ellipsis))) + ellipsis;
    }

    /**
     * Draws a texture variant's preview (its first animation frame), scaled to fit a
     * {@code size}-pixel square while keeping its aspect ratio. Shows a checkerboard
     * while it loads.
     */
    static void preview(DrawContext context, PreviewCache cache, TextureIndex index, TextureVariant variant,
                        int x, int y, int size) {
        PreviewCache.Preview preview = variant == null ? null : cache.get(index, variant);
        if (preview == null) {
            int half = size / 2;
            context.fill(x, y, x + size, y + size, 0xFF2A2A2A);
            context.fill(x, y, x + half, y + half, 0xFF3A3A3A);
            context.fill(x + half, y + half, x + size, y + size, 0xFF3A3A3A);
            return;
        }
        int width = preview.width();
        int height = preview.height();
        int drawWidth = size;
        int drawHeight = size;
        if (width > height) {
            drawHeight = Math.max(1, size * height / width);
        } else if (height > width) {
            drawWidth = Math.max(1, size * width / height);
        }
        VersionCompat.drawTexture(context, preview.id(), x + (size - drawWidth) / 2, y + (size - drawHeight) / 2,
                drawWidth, drawHeight, width, height, width, height);
    }
}
