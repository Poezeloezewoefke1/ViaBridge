package dev.texturemixer.fabric.gui;

import dev.texturemixer.core.pack.PackProblem;
import dev.texturemixer.fabric.compat.VersionCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Explains, per pack, what could not be read or may not look right, so a pack that
 * "doesn't show up" has a visible reason instead of failing silently.
 */
public class ProblemsScreen extends InputScreen {
    private final Screen parent;
    private final List<PackProblem> problems;
    private final List<Line> lines = new ArrayList<>();
    private int scroll;
    private int top;
    private int bottom;

    private record Line(OrderedText text, int color) {
    }

    public ProblemsScreen(Screen parent, List<PackProblem> problems) {
        super(Text.translatable("texturemixer.problems.title"));
        this.parent = parent;
        this.problems = problems;
    }

    @Override
    protected void init() {
        top = 28;
        bottom = height - 32;
        int textWidth = Math.min(420, width - 30);
        lines.clear();
        for (PackProblem problem : problems) {
            int color = problem.severity() == PackProblem.Severity.ERROR ? GuiDraw.RED : GuiDraw.GOLD;
            lines.add(new Line(Text.literal(problem.packName()).asOrderedText(), color));
            for (OrderedText line : textRenderer.wrapLines(Text.literal(problem.message()), textWidth - 10)) {
                lines.add(new Line(line, GuiDraw.GRAY));
            }
            lines.add(new Line(OrderedText.EMPTY, 0));
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("texturemixer.problems.open_folder"),
                        b -> Util.getOperatingSystem().open(client.getResourcePackDir().toFile()))
                .dimensions(width / 2 - 154, height - 26, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(width / 2 + 4, height - 26, 150, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        VersionCompat.renderBackgroundIfNeeded(this, context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, GuiDraw.WHITE);
        int textWidth = Math.min(420, width - 30);
        int x = (width - textWidth) / 2;
        context.fill(x - 4, top - 4, x + textWidth + 4, bottom + 4, GuiDraw.PANEL);
        context.enableScissor(x - 4, top, x + textWidth + 4, bottom);
        int y = top - scroll;
        for (Line line : lines) {
            if (y > top - 10 && y < bottom) {
                context.drawTextWithShadow(textRenderer, line.text(), x, y, line.color());
            }
            y += 10;
        }
        context.disableScissor();
    }

    @Override
    protected boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
        int max = Math.max(0, lines.size() * 10 - (bottom - top));
        scroll = Math.max(0, Math.min(max, scroll - (int) (amount * 20)));
        return true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
