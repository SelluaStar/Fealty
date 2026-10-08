package com.selluastar.fealty.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.selluastar.fealty.client.ui.Ui;
import com.selluastar.fealty.config.FealtyClientConfig;
import com.selluastar.fealty.dialogue.DialogueNode;
import com.selluastar.fealty.dialogue.DialogueService;
import com.selluastar.fealty.network.DialogueChoicePayload;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The dialogue box: a panel along the bottom of the screen with the speaker, what they say (typed out), and the
 * player's replies in one column, numbered in order (keys 1-9, or the arrow keys and Enter). Replies scroll when
 * there are more than fit. The world stays visible above it, so the speaker's bubble shows over their head too.
 */
public class DialogueScreen extends Screen {
    private static final int PORTRAIT = 58;
    private static final int PAD = 8;
    private static final int MAX_LINES = 6;
    /** Replies shown without scrolling, screen height allowing. */
    private static final int MIN_ROWS = 6;
    private static final int MAX_PANEL_WIDTH = 400;

    private final int entityId;
    private DialogueNode node;
    private long shownAt;
    private List<String> lines = List.of();
    private int totalChars;
    private float scale = 0.8F;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int textX;
    private int textWidth;
    private int optionsTop;
    private int rowHeight;
    private int visibleRows;
    private int scroll;
    private int selected;

    public DialogueScreen(int entityId, DialogueNode node) {
        super(node.name());
        this.entityId = entityId;
        this.node = node;
        this.shownAt = Util.getMillis();
    }

    public int entityId() {
        return entityId;
    }

    /** The NPC answered: show the new text and replies. */
    public void update(DialogueNode newNode) {
        this.node = newNode;
        this.shownAt = Util.getMillis();
        this.scroll = 0;
        this.selected = 0;
        rebuildWidgets();
    }

    private List<DialogueNode.Option> options() {
        return node.options();
    }

    private int lineHeight() {
        return Math.max(6, Mth.ceil(10 * scale));
    }

    @Override
    protected void init() {
        scale = FealtyClientConfig.DIALOGUE_TEXT_SCALE.get().floatValue();
        panelWidth = Math.min(width - 24, MAX_PANEL_WIDTH);
        left = (width - panelWidth) / 2;
        textX = left + PORTRAIT + 14;
        textWidth = panelWidth - PORTRAIT - 26;
        lines = new ArrayList<>();
        for (FormattedText line : font.getSplitter().splitLines(node.text(), (int) (textWidth / scale), Style.EMPTY)) {
            lines.add(line.getString());
        }
        if (lines.size() > MAX_LINES) {
            lines = new ArrayList<>(lines.subList(0, MAX_LINES));
        }
        totalChars = lines.stream().mapToInt(String::length).sum();

        rowHeight = Math.max(11, Mth.ceil(9 * scale) + 6);
        int textHeight = Math.max(2, lines.size()) * lineHeight();
        int fixed = PAD + textHeight + 6 + PAD;
        // Room for at least six replies (a conversation node's most) when the screen has it; more scroll.
        int wanted = fixed + Math.min(MIN_ROWS, options().size()) * rowHeight;
        int maxHeight = Math.max(Math.max(90, (int) (height * 0.45F)), Math.min(height - 16, wanted));
        visibleRows = Mth.clamp((maxHeight - fixed) / rowHeight, 1, Math.max(1, options().size()));
        panelHeight = Math.max(PORTRAIT + 16, fixed + visibleRows * rowHeight);
        top = height - panelHeight - 8;
        optionsTop = top + PAD + textHeight + 6;
        selected = Mth.clamp(selected, 0, Math.max(0, options().size() - 1));
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, options().size() - visibleRows);
    }

    private void choose(DialogueNode.Option option) {
        if (!option.enabled()) {
            return;
        }
        if (!finishedTyping()) {
            shownAt = 0;
        }
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        PacketDistributor.sendToServer(new DialogueChoicePayload(entityId, option.id()));
    }

    /** The NPC may go about their day again. */
    @Override
    public void removed() {
        super.removed();
        PacketDistributor.sendToServer(new DialogueChoicePayload(entityId, DialogueService.LEAVE));
    }

    private int revealed() {
        int speed = FealtyClientConfig.TYPEWRITER_SPEED.get();
        if (speed <= 0 || shownAt == 0) {
            return totalChars;
        }
        long elapsed = Util.getMillis() - shownAt;
        return (int) Math.min(totalChars, elapsed * speed / 50);
    }

    private boolean finishedTyping() {
        return revealed() >= totalChars;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Keep the world visible: only darken the bottom of the screen behind the panel.
        g.fillGradient(0, top - 30, width, height, 0x00000000, 0xA0000000);
    }

    private int rowX() {
        return textX - 2;
    }

    private int rowWidth() {
        return textWidth + 2 - (maxScroll() > 0 ? 6 : 0);
    }

    /** The option under the mouse, or -1. */
    private int optionAt(double mouseX, double mouseY) {
        if (mouseX < rowX() || mouseX >= rowX() + rowWidth() || mouseY < optionsTop) {
            return -1;
        }
        int row = (int) ((mouseY - optionsTop) / rowHeight);
        int index = scroll + row;
        return row < visibleRows && index < options().size() ? index : -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // Panel and portrait
        Ui.window(g, left, top, panelWidth, panelHeight);
        Ui.panel(g, left + 8, top + 8, PORTRAIT, panelHeight - 16);
        Entity entity = minecraft != null && minecraft.level != null ? minecraft.level.getEntity(entityId) : null;
        if (entity instanceof LivingEntity living) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, left + 10, top + 10, left + PORTRAIT + 6, top + panelHeight - 10,
                    Math.min(30, (panelHeight - 24) / 3), 0.0625F, mouseX, mouseY, living);
        }
        // Name plate above the panel
        Component name = node.name();
        int plateWidth = Math.max(font.width(name), Mth.ceil(font.width(node.subtitle()) * 0.8F)) + 16;
        Ui.hudPanel(g, left + 6, top - 24, plateWidth, 26);
        g.drawString(font, name, left + 14, top - 19, Ui.GOLD_LIGHT, true);
        Ui.scaled(g, font, node.subtitle(), left + 14, top - 9, 0.8F, Ui.CREAM, false);

        renderText(g);
        Component hint = renderOptions(g, mouseX, mouseY);
        if (hint != null) {
            g.renderTooltip(font, hint, mouseX, mouseY);
        }
    }

    /** What they say, typed out. */
    private void renderText(GuiGraphics g) {
        g.pose().pushPose();
        g.pose().translate(textX, top + PAD, 0);
        g.pose().scale(scale, scale, 1.0F);
        int remaining = revealed();
        int y = 0;
        int cursorX = 0;
        int cursorY = 0;
        for (String line : lines) {
            if (remaining <= 0) {
                break;
            }
            String shown = remaining >= line.length() ? line : line.substring(0, remaining);
            g.drawString(font, shown, 0, y, Ui.INK, false);
            cursorX = font.width(shown);
            cursorY = y;
            remaining -= line.length();
            y += 10;
        }
        if (!finishedTyping() && (Util.getMillis() / 300) % 2 == 0) {
            g.drawString(font, "_", cursorX + 1, cursorY, Ui.FADED, false);
        }
        g.pose().popPose();
    }

    /** The replies, one per row. @return the hint of the hovered reply, if it has one */
    private Component renderOptions(GuiGraphics g, int mouseX, int mouseY) {
        Ui.divider(g, rowX(), optionsTop - 5, textWidth + 2);
        int hovered = optionAt(mouseX, mouseY);
        Component hint = null;
        int x = rowX();
        int w = rowWidth();
        int iconSize = Math.max(8, Math.min(12, rowHeight - 3));
        for (int row = 0; row < visibleRows; row++) {
            int index = scroll + row;
            if (index >= options().size()) {
                break;
            }
            DialogueNode.Option option = options().get(index);
            int y = optionsTop + row * rowHeight;
            boolean lit = index == hovered || index == selected && hovered < 0;
            if (lit) {
                g.fill(x, y, x + w, y + rowHeight - 1, option.enabled() ? 0x40B8A27C : 0x20B8A27C);
            }
            int textY = y + (rowHeight - Mth.ceil(8 * scale)) / 2;
            if (index < 9) {
                Ui.scaled(g, font, Component.literal((index + 1) + "."), x + 3, textY, scale, Ui.FADED, false);
            }
            int iconX = x + 3 + Mth.ceil(font.width("9.") * scale) + 3;
            Ui.icon(g, option.icon(), iconX, y + (rowHeight - 1 - iconSize) / 2, iconSize);
            int labelX = iconX + iconSize + 4;
            int labelWidth = (int) ((x + w - labelX - 2) / scale);
            int color = option.enabled() ? (lit ? Ui.GREEN : Ui.INK) : Ui.FADED;
            Ui.scaled(g, font, Ui.fit(font, option.label(), labelWidth), labelX, textY, scale, color, false);
            if (index == hovered && !option.hint().getString().isEmpty()) {
                hint = option.hint();
            }
        }
        if (maxScroll() > 0) {
            int trackX = left + panelWidth - 12;
            int trackHeight = visibleRows * rowHeight - 1;
            g.fill(trackX, optionsTop, trackX + 3, optionsTop + trackHeight, 0x40000000);
            int thumb = Math.max(8, trackHeight * visibleRows / options().size());
            int thumbY = optionsTop + (trackHeight - thumb) * scroll / maxScroll();
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumb, 0xFF8D6E63);
        }
        return hint;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!finishedTyping() && mouseY >= top && mouseY <= top + panelHeight && optionAt(mouseX, mouseY) < 0) {
            shownAt = 0;
            return true;
        }
        int index = optionAt(mouseX, mouseY);
        if (button == 0 && index >= 0) {
            selected = index;
            choose(options().get(index));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && scrollY != 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Move the keyboard selection and keep it in view. */
    private void select(int index) {
        if (options().isEmpty()) {
            return;
        }
        selected = Mth.clamp(index, 0, options().size() - 1);
        if (selected < scroll) {
            scroll = selected;
        } else if (selected >= scroll + visibleRows) {
            scroll = selected - visibleRows + 1;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (index < options().size()) {
                select(index);
                choose(options().get(index));
                return true;
            }
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                select(selected - 1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                select(selected + 1);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (!finishedTyping()) {
                    shownAt = 0;
                } else if (selected < options().size()) {
                    choose(options().get(selected));
                }
                return true;
            }
            case GLFW.GLFW_KEY_SPACE -> {
                if (!finishedTyping()) {
                    shownAt = 0;
                    return true;
                }
            }
            default -> {
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
