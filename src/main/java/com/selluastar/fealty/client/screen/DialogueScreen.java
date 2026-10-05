package com.selluastar.fealty.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.selluastar.fealty.client.ui.FealtyButton;
import com.selluastar.fealty.client.ui.Ui;
import com.selluastar.fealty.config.FealtyClientConfig;
import com.selluastar.fealty.dialogue.DialogueNode;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The dialogue box: a panel along the bottom of the screen with the speaker, what they say (typed out), and the
 * player's replies. The world stays visible above it, so the speaker's bubble shows over their head too.
 */
public class DialogueScreen extends Screen {
    private static final int HEIGHT = 124;
    private static final int PORTRAIT = 76;

    private final int entityId;
    private DialogueNode node;
    private long shownAt;
    private List<String> lines = List.of();
    private int totalChars;
    private int left;
    private int top;
    private int panelWidth;

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
        rebuildWidgets();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(width - 24, 460);
        left = (width - panelWidth) / 2;
        top = height - HEIGHT - 8;
        int textWidth = panelWidth - PORTRAIT - 22;
        lines = new ArrayList<>();
        for (FormattedText line : font.getSplitter().splitLines(node.text(), textWidth, Style.EMPTY)) {
            lines.add(line.getString());
        }
        if (lines.size() > 4) {
            lines = lines.subList(0, 4);
        }
        totalChars = lines.stream().mapToInt(String::length).sum();

        int columns = 2;
        int buttonWidth = (textWidth - 4) / columns;
        int startY = top + 14 + Math.max(2, lines.size()) * 10 + 6;
        List<DialogueNode.Option> options = node.options();
        for (int i = 0; i < options.size() && i < 8; i++) {
            DialogueNode.Option option = options.get(i);
            int col = i % columns;
            int row = i / columns;
            Component label = Component.literal((i + 1) + ". ").append(option.label());
            FealtyButton button = new FealtyButton(left + PORTRAIT + 12 + col * (buttonWidth + 4), startY + row * 19,
                    buttonWidth, 17, label, b -> choose(option)).icon(option.icon()).enabled(option.enabled());
            if (!option.hint().getString().isEmpty()) {
                button.tooltip(option.hint());
            }
            addRenderableWidget(button);
        }
    }

    private void choose(DialogueNode.Option option) {
        if (!option.enabled()) {
            return;
        }
        if (!finishedTyping()) {
            shownAt = 0;
        }
        PacketDistributor.sendToServer(new DialogueChoicePayload(entityId, option.id()));
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        // Panel and portrait
        Ui.window(g, left, top, panelWidth, HEIGHT);
        Ui.panel(g, left + 8, top + 10, PORTRAIT - 4, HEIGHT - 20);
        Entity entity = minecraft != null && minecraft.level != null ? minecraft.level.getEntity(entityId) : null;
        if (entity instanceof LivingEntity living) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, left + 10, top + 12, left + PORTRAIT + 2, top + HEIGHT - 12, 34, 0.0625F,
                    mouseX, mouseY, living);
        }
        // Name plate above the panel
        Component name = node.name();
        int plateWidth = Math.max(font.width(name), font.width(node.subtitle())) + 16;
        Ui.hudPanel(g, left + 6, top - 24, plateWidth, 26);
        g.drawString(font, name, left + 14, top - 19, Ui.GOLD_LIGHT, true);
        g.drawString(font, node.subtitle(), left + 14, top - 9, Ui.CREAM, false);

        // What they say, typed out
        int x = left + PORTRAIT + 12;
        int y = top + 14;
        int remaining = revealed();
        int cursorX = x;
        int cursorY = y;
        for (String line : lines) {
            if (remaining <= 0) {
                break;
            }
            String shown = remaining >= line.length() ? line : line.substring(0, remaining);
            g.drawString(font, shown, x, y, Ui.INK, false);
            cursorX = x + font.width(shown);
            cursorY = y;
            remaining -= line.length();
            y += 10;
        }
        if (!finishedTyping() && (Util.getMillis() / 300) % 2 == 0) {
            g.drawString(font, "_", cursorX + 1, cursorY, Ui.FADED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!finishedTyping() && mouseY >= top && mouseY <= top + HEIGHT) {
            shownAt = 0;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (index < node.options().size()) {
                if (minecraft != null) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                choose(node.options().get(index));
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE && !finishedTyping()) {
            shownAt = 0;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
