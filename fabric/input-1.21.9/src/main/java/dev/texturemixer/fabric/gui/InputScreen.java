package dev.texturemixer.fabric.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Turns Minecraft's mouse and keyboard callbacks into the simple handlers the shared
 * screens implement. This copy is for Minecraft 1.21.9 and newer, where the callbacks
 * receive event objects ({@link Click}, {@link KeyInput}) instead of raw values.
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
    public boolean mouseClicked(Click click, boolean doubled) {
        return super.mouseClicked(click, doubled) || onMouseClicked(mouseX(), mouseY(), click.button());
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        return onMouseDragged(mouseX(), mouseY(), click.button()) || super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        onMouseReleased();
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return onMouseScrolled(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return onKeyPressed(input.getKeycode(), input.hasCtrl(), input.hasShift()) || super.keyPressed(input);
    }

    // The cursor position in GUI coordinates; equal to the event's position.
    private double mouseX() {
        return client.mouse.getScaledX(client.getWindow());
    }

    private double mouseY() {
        return client.mouse.getScaledY(client.getWindow());
    }
}
