package dev.texturemixer.fabric.gui;

import dev.texturemixer.core.TextureMixerConstants;
import dev.texturemixer.core.generate.GenerationResult;
import dev.texturemixer.core.session.MixerSession;
import dev.texturemixer.core.texture.TextureCategory;
import dev.texturemixer.core.texture.TextureEntry;
import dev.texturemixer.core.texture.TextureIndex;
import dev.texturemixer.core.texture.TextureVariant;
import dev.texturemixer.core.util.Log;
import dev.texturemixer.fabric.PreviewCache;
import dev.texturemixer.fabric.TextureMixerManager;
import dev.texturemixer.fabric.compat.VersionCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

/**
 * The texture selection screen opened by the "Mix Textures" button.
 *
 * <p>Left: a searchable, filterable grid of every texture that installed packs change.
 * Right: every available version of the clicked texture; clicking one picks it. All state
 * lives in {@link MixerSession}; this class only lays out, draws and forwards input.
 */
public class TextureMixerScreen extends InputScreen {
    private static final int ROW = 22;
    private static final int MARGIN = 6;
    private static final int SCROLLBAR = 6;

    private final Screen parent;
    private final TextureMixerManager manager;
    private final MixerSession session;
    private final PreviewCache previews;

    private TextFieldWidget search;
    private ButtonWidget viewButton;
    private ButtonWidget profileButton;
    private ButtonWidget problemsButton;
    private ButtonWidget undoButton;
    private ButtonWidget redoButton;
    private ButtonWidget resetButton;
    private ButtonWidget randomButton;
    private ButtonWidget exportButton;
    private ButtonWidget applyButton;
    private ButtonWidget bulkButton;
    private int bulkRevision = -1;
    private TextureEntry bulkEntry;
    private boolean closeAfterApply;
    private final List<ButtonWidget> tabs = new ArrayList<>();

    private int cell = 20;
    private int gridX;
    private int gridY;
    private int gridWidth;
    private int gridHeight;
    private int columns;
    private int panelX;
    private int panelWidth;
    private int optionsY;
    private int optionsBottom;
    private int statusY;

    private int gridScroll;
    private int optionsScroll;
    private boolean draggingScrollbar;
    private boolean busy;
    private Text status = Text.empty();
    private int statusColor = GuiDraw.GRAY;
    private boolean scanRequested;
    private int tabsRevision = -1;

    public TextureMixerScreen(Screen parent) {
        super(Text.translatable("texturemixer.title"));
        this.parent = parent;
        this.manager = TextureMixerManager.get();
        this.session = manager.session();
        this.previews = manager.previews();
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        tabs.clear();
        tabsRevision = -1;
        int top = MARGIN;
        int profileWidth = Math.min(130, width / 4);
        int viewWidth = Math.min(120, width / 4);
        int profileX = width - MARGIN - profileWidth;
        int viewX = profileX - 4 - viewWidth;
        int titleWidth = textRenderer.getWidth(title);
        int searchX = width >= 520 ? MARGIN + titleWidth + 10 : MARGIN;

        search = new TextFieldWidget(textRenderer, searchX, top + 1, Math.max(60, viewX - 4 - searchX), 18, Text.translatable("texturemixer.search"));
        search.setMaxLength(128);
        search.setPlaceholder(Text.translatable("texturemixer.search").formatted(net.minecraft.util.Formatting.DARK_GRAY));
        search.setText(session.query());
        search.setChangedListener(text -> {
            session.setQuery(text);
            gridScroll = 0;
        });
        addDrawableChild(search);

        viewButton = addDrawableChild(ButtonWidget.builder(viewLabel(), button -> cycleView())
                .dimensions(viewX, top, viewWidth, 20)
                .tooltip(Tooltip.of(Text.translatable("texturemixer.view.tooltip")))
                .build());
        profileButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> openProfiles())
                .dimensions(profileX, top, profileWidth, 20)
                .tooltip(Tooltip.of(Text.translatable("texturemixer.profile.tooltip")))
                .build());

        // Category tabs
        int tabY = top + 24;
        TextureCategory[] categories = TextureCategory.values();
        int tabCount = categories.length + 1;
        int tabWidth = (width - 2 * MARGIN - (tabCount - 1) * 2) / tabCount;
        for (int i = 0; i < tabCount; i++) {
            TextureCategory category = i == 0 ? null : categories[i - 1];
            Text label = Text.translatable(category == null ? "texturemixer.category.all" : "texturemixer.category." + category.key());
            ButtonWidget tab = ButtonWidget.builder(label, button -> {
                        session.setCategory(category);
                        gridScroll = 0;
                    })
                    .dimensions(MARGIN + i * (tabWidth + 2), tabY, tabWidth, 16)
                    .build();
            tabs.add(addDrawableChild(tab));
        }

        // Bottom buttons
        int bottomY = height - 24;
        int buttonCount = 7;
        int buttonWidth = Math.min(90, (width - 2 * MARGIN - (buttonCount - 1) * 4) / buttonCount);
        int rowWidth = buttonCount * buttonWidth + (buttonCount - 1) * 4;
        int x = (width - rowWidth) / 2;
        undoButton = addDrawableChild(button("texturemixer.undo", "texturemixer.undo.tooltip", x, bottomY, buttonWidth, b -> session.undo()));
        x += buttonWidth + 4;
        redoButton = addDrawableChild(button("texturemixer.redo", "texturemixer.redo.tooltip", x, bottomY, buttonWidth, b -> session.redo()));
        x += buttonWidth + 4;
        resetButton = addDrawableChild(button("texturemixer.reset", "texturemixer.reset.tooltip", x, bottomY, buttonWidth, b -> {
            session.resetAll();
            setStatus(Text.translatable("texturemixer.status.reset"), GuiDraw.GRAY);
        }));
        x += buttonWidth + 4;
        randomButton = addDrawableChild(button("texturemixer.randomize", "texturemixer.randomize.tooltip", x, bottomY, buttonWidth, b -> {
            int changed = session.randomizeVisible();
            setStatus(Text.translatable("texturemixer.status.randomized", changed), GuiDraw.GRAY);
        }));
        x += buttonWidth + 4;
        exportButton = addDrawableChild(button("texturemixer.export", "texturemixer.export.tooltip", x, bottomY, buttonWidth, b -> export()));
        x += buttonWidth + 4;
        applyButton = addDrawableChild(button("texturemixer.apply", "texturemixer.apply.tooltip", x, bottomY, buttonWidth, b -> apply()));
        x += buttonWidth + 4;
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close()).dimensions(x, bottomY, buttonWidth, 20).build());

        statusY = bottomY - 13;
        problemsButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> openProblems())
                .dimensions(width - MARGIN - 100, statusY - 5, 100, 16)
                .build());

        // Main area. Bigger cells when there is room for them.
        cell = width >= 640 ? 26 : width >= 480 ? 22 : 20;
        gridX = MARGIN;
        gridY = tabY + 22;
        int mainBottom = statusY - 9;
        gridHeight = Math.max(cell, mainBottom - gridY);
        int available = (int) ((width - 2 * MARGIN) * 0.58) - SCROLLBAR;
        columns = Math.max(4, available / cell);
        gridWidth = columns * cell;
        panelX = gridX + gridWidth + SCROLLBAR + 8;
        panelWidth = width - MARGIN - panelX;
        optionsY = gridY + 44;
        optionsBottom = gridY + gridHeight - 22;
        bulkButton = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> bulkPick())
                .dimensions(panelX + 2, optionsBottom + 2, panelWidth - 4, 18)
                .tooltip(Tooltip.of(Text.translatable("texturemixer.bulk.tooltip")))
                .build());

        session.setEnabledPackIds(VersionCompat.enabledPackIds(client.getResourcePackManager()));
        clampScroll();
        updateWidgets();

        if (!scanRequested) {
            scanRequested = true;
            startScan(false);
        }
    }

    private ButtonWidget button(String key, String tooltip, int x, int y, int width, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(Text.translatable(key), action)
                .dimensions(x, y, width, 20)
                .tooltip(Tooltip.of(Text.translatable(tooltip)))
                .build();
    }

    private int ticks;

    /** Every few seconds, picks up packs added to or changed in the folder while the screen is open. */
    @Override
    public void tick() {
        super.tick();
        if (++ticks % 60 != 0 || busy || session.index() == null || manager.isScanning()) {
            return;
        }
        TextureIndex before = session.index();
        manager.refreshIndex(false).whenComplete((index, error) -> client.execute(() -> {
            if (error == null && index != before && session.index() == before) {
                session.setIndex(index);
                clampScroll();
                setStatus(Text.translatable("texturemixer.status.packs_changed"), GuiDraw.YELLOW);
            }
        }));
    }

    private void startScan(boolean force) {
        manager.refreshIndex(force).whenComplete((index, error) -> client.execute(() -> {
            if (error != null) {
                setStatus(Text.translatable("texturemixer.status.scan_failed", message(error)), GuiDraw.RED);
                return;
            }
            session.setIndex(index);
            clampScroll();
            if (session.focusedEntry() == null && !session.visible().isEmpty()) {
                session.focus(session.visible().get(0).id());
            }
            ProfileProblem.showOnce(this);
        }));
    }

    // ------------------------------------------------------------------ state

    private enum View { PACKS, CHANGED, ALL }

    private View view() {
        if (session.onlyChanged()) {
            return View.CHANGED;
        }
        return session.includeUnmodded() ? View.ALL : View.PACKS;
    }

    private void cycleView() {
        View next = View.values()[(view().ordinal() + 1) % View.values().length];
        session.setOnlyChanged(next == View.CHANGED);
        session.setIncludeUnmodded(next == View.ALL);
        gridScroll = 0;
        viewButton.setMessage(viewLabel());
    }

    private Text viewLabel() {
        return Text.translatable("texturemixer.view." + view().name().toLowerCase(java.util.Locale.ROOT));
    }

    private void updateWidgets() {
        TextureIndex index = session.index();
        boolean ready = index != null && !busy;
        undoButton.active = ready && session.canUndo();
        redoButton.active = ready && session.canRedo();
        resetButton.active = ready && !session.selections().isEmpty();
        randomButton.active = ready && !session.visible().isEmpty();
        exportButton.active = ready;
        applyButton.active = ready;
        for (int i = 0; i < tabs.size(); i++) {
            tabs.get(i).active = session.category() != (i == 0 ? null : TextureCategory.values()[i - 1]);
        }
        if (tabsRevision != session.revision()) {
            // Labels and counts only change with the session, so skip rebuilding them every frame.
            tabsRevision = session.revision();
            applyButton.setMessage(session.hasUnappliedChanges()
                    ? Text.translatable("texturemixer.apply").formatted(net.minecraft.util.Formatting.YELLOW)
                    : Text.translatable("texturemixer.apply"));
            profileButton.setMessage(Text.translatable("texturemixer.profile", GuiDraw.trim(textRenderer, session.activeProfile(),
                    profileButton.getWidth() - 10 - textRenderer.getWidth(Text.translatable("texturemixer.profile", "").getString()))));
            viewButton.setMessage(viewLabel());
            Map<TextureCategory, Integer> counts = session.categoryCounts();
            for (int i = 0; i < tabs.size(); i++) {
                TextureCategory category = i == 0 ? null : TextureCategory.values()[i - 1];
                int count = category == null ? counts.values().stream().mapToInt(Integer::intValue).sum() : counts.getOrDefault(category, 0);
                tabs.get(i).setTooltip(Tooltip.of(Text.translatable("texturemixer.category.count", count)));
            }
        }

        TextureEntry focused = index == null ? null : session.focusedEntry();
        bulkButton.visible = focused != null && session.visible().size() > 1;
        if (bulkButton.visible && (bulkRevision != session.revision() || bulkEntry != focused)) {
            bulkRevision = session.revision();
            bulkEntry = focused;
            String choice = session.choice(focused.id());
            int count = session.countVisibleWith(choice);
            Text label = choice == null
                    ? Text.translatable("texturemixer.bulk.reset", count)
                    : Text.translatable("texturemixer.bulk.use", sourceName(index, choice), count);
            bulkButton.setMessage(Text.literal(GuiDraw.trim(textRenderer, label.getString(), bulkButton.getWidth() - 8)));
        }
        bulkButton.active = !busy;

        int problems = index == null ? 0 : index.problems().size();
        problemsButton.visible = problems > 0;
        problemsButton.setMessage(Text.translatable("texturemixer.problems", problems));
    }

    private void setStatus(Text text, int color) {
        status = text;
        statusColor = color;
    }

    // ------------------------------------------------------------------ actions

    private void apply() {
        if (busy || session.index() == null) {
            return;
        }
        busy = true;
        setStatus(Text.translatable("texturemixer.status.applying"), GuiDraw.YELLOW);
        manager.apply(parent).whenComplete((result, error) -> client.execute(() -> {
            busy = false;
            if (error != null) {
                Log.LOGGER.error("Applying the mixed pack failed", error);
                setStatus(Text.translatable("texturemixer.status.apply_failed", message(error)), GuiDraw.RED);
            } else {
                setStatus(resultText("texturemixer.status.applied", result), result.skipped().isEmpty() ? GuiDraw.GREEN : GuiDraw.GOLD);
                if (closeAfterApply) {
                    leave();
                    return;
                }
            }
            closeAfterApply = false;
        }));
    }

    private void export() {
        if (busy || session.index() == null) {
            return;
        }
        busy = true;
        setStatus(Text.translatable("texturemixer.status.exporting"), GuiDraw.YELLOW);
        manager.export().whenComplete((result, error) -> client.execute(() -> {
            busy = false;
            if (error != null) {
                Log.LOGGER.error("Export failed", error);
                setStatus(Text.translatable("texturemixer.status.export_failed", message(error)), GuiDraw.RED);
                return;
            }
            setStatus(resultText("texturemixer.status.exported", result), result.skipped().isEmpty() ? GuiDraw.GREEN : GuiDraw.GOLD);
            String file = java.nio.file.Path.of(result.target()).getFileName().toString();
            client.setScreen(new ConfirmScreen(open -> {
                if (open) {
                    Util.getOperatingSystem().open(manager.exportDirectory().toFile());
                }
                client.setScreen(this);
            }, Text.translatable("texturemixer.export.done.title"), Text.translatable("texturemixer.export.done.message", file, result.written())));
        }));
    }

    /** Uses the focused texture's pick (or "unchanged") for every texture in the list. */
    private void bulkPick() {
        TextureEntry focused = session.focusedEntry();
        if (focused == null) {
            return;
        }
        String choice = session.choice(focused.id());
        int changed = session.chooseForVisible(choice);
        setStatus(choice == null
                ? Text.translatable("texturemixer.status.bulk_reset", changed)
                : Text.translatable("texturemixer.status.bulk_used", sourceName(session.index(), choice), changed), GuiDraw.GRAY);
    }

    private Text resultText(String key, GenerationResult result) {
        Text text = Text.translatable(key, result.written());
        if (!result.skipped().isEmpty()) {
            text = Text.empty().append(text).append(" ").append(Text.translatable("texturemixer.status.skipped", result.skipped().size()));
        }
        return text;
    }

    private void openProfiles() {
        client.setScreen(new ProfilesScreen(this, session));
    }

    private void openProblems() {
        if (session.index() != null) {
            client.setScreen(new ProblemsScreen(this, session.index().problems()));
        }
    }

    static String message(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause.getCause() != null && cause.getMessage() == null) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    /** Asks whether to apply first when there are picks that are not in game yet. */
    @Override
    public void close() {
        if (busy || session.index() == null || !session.hasUnappliedChanges()) {
            leave();
            return;
        }
        client.setScreen(new ConfirmScreen(apply -> {
            client.setScreen(this);
            if (apply) {
                closeAfterApply = true;
                apply();
            } else {
                leave();
            }
        }, Text.translatable("texturemixer.leave.title"), Text.translatable("texturemixer.leave.message"),
                Text.translatable("texturemixer.apply"), Text.translatable("texturemixer.leave.later")));
    }

    private void leave() {
        session.saveAsync();
        previews.clear();
        client.setScreen(parent);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        VersionCompat.renderBackgroundIfNeeded(this, context, mouseX, mouseY, delta);
        updateWidgets();
        super.render(context, mouseX, mouseY, delta);
        previews.beginFrame();

        if (width >= 520) {
            context.drawTextWithShadow(textRenderer, title, MARGIN, MARGIN + 6, GuiDraw.WHITE);
        }
        TextureIndex index = session.index();
        renderGrid(context, index, mouseX, mouseY);
        renderPanel(context, index, mouseX, mouseY);
        renderStatus(context, index);

        TextureEntry hovered = index == null ? null : entryAt(mouseX, mouseY);
        if (hovered != null) {
            renderHoverCard(context, index, hovered, mouseX, mouseY);
        }
    }
            lines.add(Text.translatable("texturemixer.tooltip.versions", hovered.variants().size()).formatted(net.minecraft.util.Formatting.DARK_GRAY));
            if (choice != null) {
                lines.add(Text.translatable("texturemixer.tooltip.right_click").formatted(net.minecraft.util.Formatting.DARK_GRAY));
            }
            context.drawTooltip(textRenderer, lines, mouseX, mouseY);
        }
    }

    /** A card next to the cursor with a large preview and what is picked for the texture. */
    private void renderHoverCard(DrawContext context, TextureIndex index, TextureEntry entry, int mouseX, int mouseY) {
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        lines.add(entry.id().toString());
        colors.add(GuiDraw.WHITE);
        String choice = session.choice(entry.id());
        if (choice != null) {
            lines.add(Text.translatable("texturemixer.tooltip.picked", sourceName(index, choice)).getString());
            colors.add(GuiDraw.GREEN);
        } else {
            lines.add(Text.translatable("texturemixer.tooltip.unchanged", sourceName(index, session.currentSource(entry))).getString());
            colors.add(GuiDraw.GRAY);
        }
        lines.add(Text.translatable("texturemixer.tooltip.versions", entry.variants().size()).getString());
        colors.add(GuiDraw.DARK_GRAY);
        if (choice != null) {
            lines.add(Text.translatable("texturemixer.tooltip.right_click").getString());
            colors.add(GuiDraw.DARK_GRAY);
        }

        int image = 48;
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, textRenderer.getWidth(line));
        }
        textWidth = Math.min(textWidth, Math.max(80, width / 2));
        int cardWidth = 6 + image + 6 + textWidth + 6;
        int cardHeight = Math.max(image + 12, lines.size() * 10 + 10);
        int x = mouseX + 12;
        int y = mouseY + 4;
        if (x + cardWidth > width - 2) {
            x = Math.max(2, mouseX - 12 - cardWidth);
        }
        if (y + cardHeight > height - 2) {
            y = Math.max(2, height - 2 - cardHeight);
        }
        context.fill(x, y, x + cardWidth, y + cardHeight, 0xF0100010);
        GuiDraw.border(context, x, y, cardWidth, cardHeight, 0xFF5000FF);
        String effective = session.effectiveSource(entry);
        TextureVariant variant = effective == null ? entry.variants().get(0) : entry.variant(effective);
        GuiDraw.preview(context, previews, index, variant, x + 6, y + 6, image);
        int textX = x + 6 + image + 6;
        for (int i = 0; i < lines.size(); i++) {
            GuiDraw.text(context, textRenderer, lines.get(i), textX, y + 6 + i * 10, textWidth, colors.get(i));
        }
    }

    private void renderGrid(DrawContext context, TextureIndex index, int mouseX, int mouseY) {
        context.fill(gridX - 1, gridY - 1, gridX + gridWidth + SCROLLBAR + 1, gridY + gridHeight + 1, GuiDraw.PANEL);
        if (index == null) {
            String text = manager.isScanning() || manager.scanStatus().isEmpty()
                    ? Text.translatable("texturemixer.scanning").getString() + " " + manager.scanStatus()
                    : manager.scanStatus();
            GuiDraw.text(context, textRenderer, text, gridX + 6, gridY + 6, gridWidth - 12, GuiDraw.GRAY);
            return;
        }
        List<TextureEntry> entries = session.visible();
        if (entries.isEmpty()) {
            String key = index.size() == 0 ? "texturemixer.empty.no_packs"
                    : session.onlyChanged() ? "texturemixer.empty.no_changes" : "texturemixer.empty.no_match";
            List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(Text.translatable(key), gridWidth - 12);
            int y = gridY + 6;
            for (net.minecraft.text.OrderedText line : lines) {
                context.drawTextWithShadow(textRenderer, line, gridX + 6, y, GuiDraw.GRAY);
                y += 10;
            }
            return;
        }

        context.enableScissor(gridX, gridY, gridX + gridWidth, gridY + gridHeight);
        TextureEntry focused = session.focusedEntry();
        int firstRow = gridScroll / cell;
        int offset = gridScroll % cell;
        int visibleRows = gridHeight / cell + 2;
        for (int row = 0; row < visibleRows; row++) {
            for (int column = 0; column < columns; column++) {
                int i = (firstRow + row) * columns + column;
                if (i >= entries.size()) {
                    break;
                }
                TextureEntry entry = entries.get(i);
                int x = gridX + column * cell;
                int y = gridY + row * cell - offset;
                boolean hover = mouseX >= x && mouseX < x + cell && mouseY >= y && mouseY < y + cell
                        && mouseY >= gridY && mouseY < gridY + gridHeight;
                if (hover) {
                    context.fill(x, y, x + cell, y + cell, GuiDraw.HOVER);
                }
                String effective = session.effectiveSource(entry);
                TextureVariant variant = effective == null ? entry.variants().get(0) : entry.variant(effective);
                GuiDraw.preview(context, previews, index, variant, x + 2, y + 2, cell - 4);
                String choice = session.choice(entry.id());
                if (choice != null) {
                    int color = TextureMixerConstants.VANILLA_SOURCE_ID.equals(choice) ? GuiDraw.GOLD : GuiDraw.GREEN;
                    context.fill(x + cell - 6, y + 1, x + cell - 1, y + 6, 0xFF000000);
                    context.fill(x + cell - 5, y + 2, x + cell - 2, y + 5, color);
                }
                if (focused != null && focused.id().equals(entry.id())) {
                    GuiDraw.border(context, x, y, cell, cell, GuiDraw.WHITE);
                }
            }
        }
        context.disableScissor();

        // Scrollbar
        int contentHeight = rows() * cell;
        int trackX = gridX + gridWidth;
        context.fill(trackX, gridY, trackX + SCROLLBAR, gridY + gridHeight, 0x40000000);
        if (contentHeight > gridHeight) {
            int thumbHeight = Math.max(12, gridHeight * gridHeight / contentHeight);
            int thumbY = gridY + (int) ((long) (gridHeight - thumbHeight) * gridScroll / (contentHeight - gridHeight));
            context.fill(trackX + 1, thumbY, trackX + SCROLLBAR - 1, thumbY + thumbHeight, draggingScrollbar ? 0xFFFFFFFF : 0xFFA0A0A0);
        }
    }

    private void renderPanel(DrawContext context, TextureIndex index, int mouseX, int mouseY) {
        int panelBottom = gridY + gridHeight;
        context.fill(panelX - 1, gridY - 1, panelX + panelWidth + 1, panelBottom + 1, GuiDraw.PANEL);
        TextureEntry entry = index == null ? null : session.focusedEntry();
        if (entry == null) {
            if (index != null && !session.visible().isEmpty()) {
                GuiDraw.text(context, textRenderer, Text.translatable("texturemixer.panel.hint").getString(), panelX + 4, gridY + 6, panelWidth - 8, GuiDraw.GRAY);
            }
            return;
        }

        // Header: large preview of what will be shown, and the texture's identity.
        String effective = session.effectiveSource(entry);
        GuiDraw.animatedPreview(context, previews, index, effective == null ? null : entry.variant(effective), panelX + 4, gridY + 4, 32);
        int textX = panelX + 42;
        int textWidth = panelWidth - 46;
        GuiDraw.text(context, textRenderer, entry.id().shortName(), textX, gridY + 4, textWidth, GuiDraw.WHITE);
        GuiDraw.text(context, textRenderer, entry.id().toString(), textX, gridY + 15, textWidth, GuiDraw.DARK_GRAY);
        String choice = session.choice(entry.id());
        Text state = choice == null
                ? Text.translatable("texturemixer.panel.unchanged")
                : Text.translatable("texturemixer.panel.picked", sourceName(index, choice));
        GuiDraw.text(context, textRenderer, state.getString(), textX, gridY + 26, textWidth, choice == null ? GuiDraw.GRAY : GuiDraw.GREEN);

        // Options
        List<Option> options = options(entry);
        int listBottom = optionsBottom;
        int listHeight = listBottom - optionsY;
        context.enableScissor(panelX, optionsY, panelX + panelWidth, listBottom);
        for (int i = 0; i < options.size(); i++) {
            Option option = options.get(i);
            int y = optionsY + i * ROW - optionsScroll;
            if (y + ROW < optionsY || y > listBottom) {
                continue;
            }
            boolean selected = option.sourceId == null ? choice == null : option.sourceId.equals(choice);
            boolean hover = option.enabled && mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= y && mouseY < y + ROW
                    && mouseY >= optionsY && mouseY < listBottom;
            if (selected) {
                context.fill(panelX + 1, y, panelX + panelWidth - 1, y + ROW - 1, GuiDraw.SELECTED);
                GuiDraw.border(context, panelX + 1, y, panelWidth - 2, ROW - 1, GuiDraw.GREEN);
            } else if (hover) {
                context.fill(panelX + 1, y, panelX + panelWidth - 1, y + ROW - 1, GuiDraw.HOVER);
            }
            GuiDraw.animatedPreview(context, previews, index, option.variant, panelX + 3, y + 2, ROW - 5);
            int labelX = panelX + ROW + 2;
            int labelWidth = panelWidth - ROW - 5;
            String number = i < 9 ? (i + 1) + " " : "";
            GuiDraw.text(context, textRenderer, number + option.label, labelX, y + 2, labelWidth, option.enabled ? GuiDraw.WHITE : GuiDraw.DARK_GRAY);
            GuiDraw.text(context, textRenderer, option.info, labelX, y + 12, labelWidth, option.infoColor);
        }
        context.disableScissor();
        if (options.size() * ROW > listHeight) {
            GuiDraw.text(context, textRenderer, "↕", panelX + panelWidth - 8, listBottom - 10, 8, GuiDraw.DARK_GRAY);
        }
    }

    private void renderStatus(DrawContext context, TextureIndex index) {
        Text line = status;
        int color = statusColor;
        if (line.getString().isEmpty()) {
            if (index == null) {
                line = Text.translatable("texturemixer.scanning");
                color = GuiDraw.GRAY;
            } else {
                int packs = Math.max(0, index.sources().size() - 1);
                line = Text.translatable("texturemixer.status.summary", session.visible().size(), packs, session.selections().size());
                color = GuiDraw.GRAY;
            }
        }
        int maxWidth = width - 2 * MARGIN - (problemsButton.visible ? problemsButton.getWidth() + 6 : 0);
        GuiDraw.text(context, textRenderer, line.getString(), MARGIN, statusY, maxWidth, color);
    }

    /** One row in the versions list: "keep unchanged", Minecraft's own, then each pack. */
    private record Option(String sourceId, TextureVariant variant, String label, String info, int infoColor, boolean enabled) {
    }

    private List<Option> cachedOptions = List.of();
    private TextureEntry cachedOptionsEntry;
    private int cachedOptionsRevision = -1;

    /** The versions list; rebuilt only when the texture or something visible changed. */
    private List<Option> options(TextureEntry entry) {
        if (entry != cachedOptionsEntry || session.revision() != cachedOptionsRevision) {
            cachedOptions = buildOptions(entry);
            cachedOptionsEntry = entry;
            cachedOptionsRevision = session.revision();
        }
        return cachedOptions;
    }

    private List<Option> buildOptions(TextureEntry entry) {
        TextureIndex index = session.index();
        List<Option> options = new ArrayList<>();
        String current = session.currentSource(entry);
        options.add(new Option(null, current == null ? null : entry.variant(current),
                Text.translatable("texturemixer.option.unchanged").getString(),
                Text.translatable("texturemixer.option.unchanged.info", sourceName(index, current)).getString(), GuiDraw.GRAY, true));
        for (TextureVariant variant : entry.variants()) {
            StringBuilder info = new StringBuilder();
            info.append(variant.width()).append('×').append(variant.height());
            if (variant.animated()) {
                info.append(" · ").append(Text.translatable("texturemixer.option.animated").getString());
            }
            if (variant.sourceId().equals(current)) {
                info.append(" · ").append(Text.translatable("texturemixer.option.in_use").getString());
            }
            int infoColor = GuiDraw.GRAY;
            boolean enabled = true;
            if (variant.legacyPath()) {
                info.setLength(0);
                info.append(Text.translatable("texturemixer.option.legacy").getString());
                infoColor = GuiDraw.RED;
                enabled = false;
            }
            options.add(new Option(variant.sourceId(), variant, sourceName(index, variant.sourceId()), info.toString(), infoColor, enabled));
        }
        return options;
    }

    static String sourceName(TextureIndex index, String sourceId) {
        if (sourceId == null) {
            return Text.translatable("texturemixer.source.none").getString();
        }
        if (TextureMixerConstants.VANILLA_SOURCE_ID.equals(sourceId)) {
            return Text.translatable("texturemixer.source.vanilla").getString();
        }
        return index == null ? sourceId : index.sourceName(sourceId);
    }

    // ------------------------------------------------------------------ input

    private int rows() {
        return (session.visible().size() + columns - 1) / columns;
    }

    private void clampScroll() {
        int max = Math.max(0, rows() * cell - gridHeight);
        gridScroll = Math.max(0, Math.min(gridScroll, max));
    }

    private TextureEntry entryAt(double mouseX, double mouseY) {
        if (mouseX < gridX || mouseX >= gridX + gridWidth || mouseY < gridY || mouseY >= gridY + gridHeight) {
            return null;
        }
        int column = (int) (mouseX - gridX) / cell;
        int row = (int) (mouseY - gridY + gridScroll) / cell;
        int i = row * columns + column;
        List<TextureEntry> entries = session.visible();
        return column < columns && i >= 0 && i < entries.size() ? entries.get(i) : null;
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (session.index() == null) {
            return false;
        }
        int trackX = gridX + gridWidth;
        if (button == 0 && mouseX >= trackX && mouseX < trackX + SCROLLBAR && mouseY >= gridY && mouseY < gridY + gridHeight) {
            draggingScrollbar = true;
            scrollTo(mouseY);
            return true;
        }
        TextureEntry entry = entryAt(mouseX, mouseY);
        if (entry != null) {
            if (button == 1) {
                session.choose(entry.id(), null);
            } else {
                session.focus(entry.id());
                optionsScroll = 0;
            }
            return true;
        }
        TextureEntry focused = session.focusedEntry();
        if (focused != null && mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= optionsY && mouseY < optionsBottom) {
            int i = (int) (mouseY - optionsY + optionsScroll) / ROW;
            List<Option> options = options(focused);
            if (i >= 0 && i < options.size() && options.get(i).enabled) {
                session.choose(focused.id(), options.get(i).sourceId);
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean onMouseDragged(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            scrollTo(mouseY);
            return true;
        }
        return false;
    }

    @Override
    protected void onMouseReleased() {
        draggingScrollbar = false;
    }

    private void scrollTo(double mouseY) {
        int contentHeight = rows() * cell;
        if (contentHeight <= gridHeight) {
            return;
        }
        double fraction = (mouseY - gridY) / gridHeight;
        gridScroll = (int) (fraction * (contentHeight - gridHeight) * 1.1);
        clampScroll();
    }

    @Override
    protected boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= gridX && mouseX < gridX + gridWidth + SCROLLBAR && mouseY >= gridY && mouseY < gridY + gridHeight) {
            gridScroll -= (int) (amount * cell * 2);
            clampScroll();
            return true;
        }
        TextureEntry focused = session.focusedEntry();
        if (focused != null && mouseX >= panelX && mouseX < panelX + panelWidth && mouseY >= optionsY && mouseY < optionsBottom) {
            int max = Math.max(0, options(focused).size() * ROW - (optionsBottom - optionsY));
            optionsScroll = Math.max(0, Math.min(max, optionsScroll - (int) (amount * ROW)));
            return true;
        }
        return false;
    }

    @Override
    protected boolean onKeyPressed(int keyCode, boolean control, boolean shift) {
        if (!control && search.isFocused()) {
            return false; // typing in the search box
        }
        if (control && keyCode == GLFW.GLFW_KEY_S) {
            apply();
            return true;
        }
        if (!control && session.index() != null && !busy && handleNavigationKey(keyCode)) {
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_Z && session.index() != null && !busy) {
            if (shift) {
                session.redo();
            } else {
                session.undo();
            }
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_Y && session.index() != null && !busy) {
            session.redo();
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_F) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F5) {
            startScanForced();
            return true;
        }
        return false;
    }

    /** Arrow keys move through the grid, 1-9 pick a version, Delete returns to unchanged. */
    private boolean handleNavigationKey(int keyCode) {
        List<TextureEntry> entries = session.visible();
        if (entries.isEmpty()) {
            return false;
        }
        TextureEntry focused = session.focusedEntry();
        int index = focused == null ? -1 : entries.indexOf(focused);
        int target = switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> index - 1;
            case GLFW.GLFW_KEY_RIGHT -> index + 1;
            case GLFW.GLFW_KEY_UP -> index - columns;
            case GLFW.GLFW_KEY_DOWN -> index < 0 ? 0 : index + columns;
            default -> Integer.MIN_VALUE;
        };
        if (target != Integer.MIN_VALUE) {
            target = Math.max(0, Math.min(entries.size() - 1, target));
            session.focus(entries.get(target).id());
            optionsScroll = 0;
            scrollToShow(target);
            return true;
        }
        if (focused == null) {
            return false;
        }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            List<Option> options = options(focused);
            int option = keyCode - GLFW.GLFW_KEY_1;
            if (option < options.size() && options.get(option).enabled) {
                session.choose(focused.id(), options.get(option).sourceId);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            session.choose(focused.id(), null);
            return true;
        }
        return false;
    }

    private void scrollToShow(int entryIndex) {
        int top = (entryIndex / columns) * cell;
        if (top < gridScroll) {
            gridScroll = top;
        } else if (top + cell > gridScroll + gridHeight) {
            gridScroll = top + cell - gridHeight;
        }
        clampScroll();
    }

    private void startScanForced() {
        setStatus(Text.translatable("texturemixer.status.rescanning"), GuiDraw.GRAY);
        session.setIndex(null);
        startScan(true);
        status = Text.empty();
    }

    /** Shows a profile-file loading problem once per game session. */
    private static final class ProfileProblem {
        private static boolean shown;

        static void showOnce(TextureMixerScreen screen) {
            String problem = screen.session.profiles().loadProblem();
            if (!shown && problem != null) {
                shown = true;
                screen.setStatus(Text.literal(problem), GuiDraw.RED);
            }
        }
    }
}
