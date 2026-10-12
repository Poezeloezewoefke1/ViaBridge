package dev.texturemixer.fabric;

import dev.texturemixer.core.util.Log;
import dev.texturemixer.fabric.compat.VersionCompat;
import dev.texturemixer.fabric.gui.TextureMixerScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Entry point. Adds the "Mix Textures" button to the vanilla resource pack screen;
 * everything else happens once the player presses it. The pack screen itself is not
 * modified, so enabling, disabling and reordering packs keep working as usual.
 */
public final class TextureMixerClient implements ClientModInitializer {
    public static final String BUTTON_KEY = "texturemixer.button.mix";

    @Override
    public void onInitializeClient() {
        Log.LOGGER.info("Texture Mixer loaded for Minecraft {} (pack format {})",
                TextureMixerManager.gameVersion(), VersionCompat.PACK_FORMAT);

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!TextureMixerManager.get().isResourcePackScreen(screen)) {
                return;
            }
            int buttonWidth = Math.min(100, Math.max(70, scaledWidth / 6));
            ButtonWidget button = ButtonWidget.builder(Text.translatable(BUTTON_KEY),
                            pressed -> client.setScreen(new TextureMixerScreen(screen)))
                    .dimensions(scaledWidth - buttonWidth - 6, 6, buttonWidth, 20)
                    .tooltip(Tooltip.of(Text.translatable("texturemixer.button.mix.tooltip")))
                    .build();
            Screens.getButtons(screen).add(button);
            // Keep it in the corner even if the window is resized without re-initializing
            // the screen. Fabric clears these listeners whenever the screen is initialized.
            ScreenEvents.beforeRender(screen).register((current, context, mouseX, mouseY, delta) ->
                    button.setX(current.width - button.getWidth() - 6));
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> TextureMixerManager.get().cancelScan());
    }
}
