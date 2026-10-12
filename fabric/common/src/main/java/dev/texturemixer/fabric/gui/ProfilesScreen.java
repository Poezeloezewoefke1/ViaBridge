package dev.texturemixer.fabric.gui;

import dev.texturemixer.core.profile.ProfileStore;
import dev.texturemixer.core.session.MixerSession;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import dev.texturemixer.fabric.compat.VersionCompat;

import java.util.List;

/**
 * Lists the saved texture profiles and lets the player switch, create, duplicate,
 * rename and delete them. Switching only changes which selections are being edited;
 * press Apply on the main screen to put a profile's textures in game.
 */
public class ProfilesScreen extends InputScreen {
    private static final int ROW = 18;

    private final Screen parent;
    private final MixerSession session;
    private TextFieldWidget nameField;
    private ButtonWidget useButton;
    private ButtonWidget renameButton;
    private ButtonWidget deleteButton;
    private String selected;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private int scroll;
    private Text message = Text.empty();
    private int messageColor = GuiDraw.GRAY;

    public ProfilesScreen(Screen parent, MixerSession session) {
        super(Text.translatable("texturemixer.profiles.title"));
        this.parent = parent;
        this.session = session;
        this.selected = session.activeProfile();
    }

    @Override
    protected void init() {
        listWidth = Math.min(260, width - 20);
        listX = (width - listWidth) / 2;
        listY = 30;
        listHeight = Math.max(ROW * 2, height - 30 - 92);

        nameField = new TextFieldWidget(textRenderer, listX, height - 74, listWidth, 18, Text.translatable("texturemixer.profiles.name"));
        nameField.setMaxLength(ProfileStore.MAX_NAME_LENGTH);
        nameField.setPlaceholder(Text.translatable("texturemixer.profiles.name").formatted(net.minecraft.util.Formatting.DARK_GRAY));
        addDrawableChild(nameField);

        int buttonWidth = Math.min(70, (width - 20 - 4 * 4) / 5);
        int rowWidth = 5 * buttonWidth + 4 * 4;
        int x = (width - rowWidth) / 2;
        int y = height - 50;
        useButton = addDrawableChild(button("texturemixer.profiles.use", "texturemixer.profiles.use.tooltip", x, y, buttonWidth, b -> use()));
        x += buttonWidth + 4;
        addDrawableChild(button("texturemixer.profiles.new", "texturemixer.profiles.new.tooltip", x, y, buttonWidth, b -> create(false)));
        x += buttonWidth + 4;
        addDrawableChild(button("texturemixer.profiles.duplicate", "texturemixer.profiles.duplicate.tooltip", x, y, buttonWidth, b -> create(true)));
        x += buttonWidth + 4;
        renameButton = addDrawableChild(button("texturemixer.profiles.rename", "texturemixer.profiles.rename.tooltip", x, y, buttonWidth, b -> rename()));
        x += buttonWidth + 4;
        deleteButton = addDrawableChild(button("texturemixer.profiles.delete", "texturemixer.profiles.delete.tooltip", x, y, buttonWidth, b -> delete()));

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(width / 2 - 75, height - 26, 150, 20).build());
    }

    private ButtonWidget button(String key, String tooltip, int x, int y, int width, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(Text.translatable(key), action)
                .dimensions(x, y, width, 20)
                .tooltip(Tooltip.of(Text.translatable(tooltip)))
                .build();
    }

    private void use() {
        if (selected != null) {
            session.switchProfile(selected);
            info(Text.translatable("texturemixer.profiles.switched", selected));
        }
    }

    private void create(boolean duplicate) {
        String typed = nameField.getText().trim();
        String source = selected != null ? selected : session.activeProfile();
        String name = !typed.isEmpty() ? typed
                : session.profiles().uniqueName(duplicate ? source + " copy" : Text.translatable("texturemixer.profiles.default_name").getString());
        if (duplicate) {
            session.switchProfile(source);
        }
        String error = session.createProfile(name, duplicate);
        if (error != null) {
            error(error);
            return;
        }
        selected = session.activeProfile();
        nameField.setText("");
        info(Text.translatable("texturemixer.profiles.created", selected));
    }

    private void rename() {
        String typed = nameField.getText().trim();
        if (selected == null || typed.isEmpty()) {
            error(Text.translatable("texturemixer.profiles.type_name").getString());
            return;
        }
        String error = session.renameProfile(selected, typed);
        if (error != null) {
            error(error);
            return;
        }
        selected = typed;
        nameField.setText("");
        info(Text.translatable("texturemixer.profiles.renamed", typed));
    }

    private void delete() {
        if (selected == null) {
            return;
        }
        String name = selected;
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                String error = session.deleteProfile(name);
                if (error != null) {
                    error(error);
                } else {
                    selected = session.activeProfile();
                    info(Text.translatable("texturemixer.profiles.deleted", name));
                }
            }
            client.setScreen(this);
        }, Text.translatable("texturemixer.profiles.delete.confirm.title"),
                Text.translatable("texturemixer.profiles.delete.confirm.message", name)));
    }

    private void info(Text text) {
        message = text;
        messageColor = GuiDraw.GREEN;
    }

    private void error(String text) {
        message = Text.literal(text);
        messageColor = GuiDraw.RED;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        VersionCompat.renderBackgroundIfNeeded(this, context, mouseX, mouseY, delta);
        List<String> names = session.profiles().names();
        if (selected != null && !names.contains(selected)) {
            selected = session.activeProfile();
        }
        useButton.active = selected != null && !selected.equals(session.activeProfile());
        renameButton.active = selected != null;
        deleteButton.active = selected != null && names.size() > 1;
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, GuiDraw.WHITE);
        context.fill(listX - 1, listY - 1, listX + listWidth + 1, listY + listHeight + 1, GuiDraw.PANEL);
        context.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int y = listY + i * ROW - scroll;
            if (y + ROW < listY || y > listY + listHeight) {
                continue;
            }
            boolean isSelected = name.equals(selected);
            boolean hover = mouseX >= listX && mouseX < listX + listWidth && mouseY >= y && mouseY < y + ROW
                    && mouseY >= listY && mouseY < listY + listHeight;
            if (isSelected) {
                context.fill(listX + 1, y, listX + listWidth - 1, y + ROW - 1, GuiDraw.SELECTED);
                GuiDraw.border(context, listX + 1, y, listWidth - 2, ROW - 1, GuiDraw.WHITE);
            } else if (hover) {
                context.fill(listX + 1, y, listX + listWidth - 1, y + ROW - 1, GuiDraw.HOVER);
            }
            boolean active = name.equals(session.activeProfile());
            int count = session.profiles().get(name).size();
            String suffix = Text.translatable("texturemixer.profiles.count", count).getString();
            int suffixWidth = textRenderer.getWidth(suffix);
            GuiDraw.text(context, textRenderer, (active ? "▶ " : "   ") + name, listX + 5, y + 5,
                    listWidth - suffixWidth - 16, active ? GuiDraw.GREEN : GuiDraw.WHITE);
            context.drawTextWithShadow(textRenderer, suffix, listX + listWidth - suffixWidth - 5, y + 5, GuiDraw.GRAY);
        }
        context.disableScissor();
        GuiDraw.text(context, textRenderer, message.getString(), listX, height - 86, listWidth, messageColor);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= listX && mouseX < listX + listWidth && mouseY >= listY && mouseY < listY + listHeight) {
            int i = (int) (mouseY - listY + scroll) / ROW;
            List<String> names = session.profiles().names();
            if (i >= 0 && i < names.size()) {
                String clicked = names.get(i);
                if (clicked.equals(selected) && !clicked.equals(session.activeProfile())) {
                    use(); // second click on the same row switches to it
                }
                selected = clicked;
                nameField.setText(clicked);
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        int max = Math.max(0, session.profiles().names().size() * ROW - listHeight);
        scroll = Math.max(0, Math.min(max, scroll - (int) (amount * ROW)));
        return true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
