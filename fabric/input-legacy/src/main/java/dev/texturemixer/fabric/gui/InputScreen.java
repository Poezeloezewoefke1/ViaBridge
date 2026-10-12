package dev.texturemixer.fabric.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Turns Minecraft's mouse and keyboard callbacks into the simple handlers the shared
 * screens implement. This copy is for Minecraft 1.20 - 1.21.8, where the callbacks take
 * raw coordinates, button numbers and key codes. Minecraft 1.21.9 changed them to event
 * objects; see {@code fabric/input-1.21.9}.
 *
 * <p>Widgets (buttons, text fields) always get the event first.
 */
public abstract class InputScreen extends Screen {
    protected InputScreen(Text title) {
        super(title);
    }

    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    protected boolean onMouseDragged(double mouseX, double mouseY, int button) {
        return false;
    }

    protected void onMouseReleased() {
    }

    protected boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    protected boolean onKeyPressed(int keyCode, boolean control, boolean shift) {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button) || onMouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return onMouseDragged(mouseX, mouseY, button) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        onMouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // No @Override on the two scroll methods: 1.20.2 added a horizontal amount, so each
    // version overrides exactly one of them and the other is simply unused.

    /** Minecraft 1.20.2 - 1.21.8. */
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return onMouseScrolled(mouseX, mouseY, verticalAmount);
    }

    /** Minecraft 1.20.1. */
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return onMouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean control = (modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        return onKeyPressed(keyCode, control, shift) || super.keyPressed(keyCode, scanCode, modifiers);
    }
}
