package com.selluastar.fealty.client.ui;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** A wooden Fealty button with an optional icon and a choice of click sound. */
public class FealtyButton extends AbstractButton {
    private final Consumer<FealtyButton> onPress;
    @Nullable
    private String icon;
    private SoundEvent sound = SoundEvents.UI_BUTTON_CLICK.value();
    private float pitch = 1.0F;

    public FealtyButton(int x, int y, int width, int height, Component message, Consumer<FealtyButton> onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public FealtyButton icon(@Nullable String name) {
        this.icon = name;
        return this;
    }

    public FealtyButton tooltip(@Nullable Component text) {
        setTooltip(text == null ? null : Tooltip.create(text));
        return this;
    }

    /** Page-turn click, for book-like screens. */
    public FealtyButton pageSound() {
        this.sound = SoundEvents.BOOK_PAGE_TURN;
        this.pitch = 1.0F;
        return this;
    }

    public FealtyButton sound(SoundEvent event, float pitch) {
        this.sound = event;
        this.pitch = pitch;
        return this;
    }

    public FealtyButton enabled(boolean enabled) {
        this.active = enabled;
        return this;
    }

    @Override
    public void onPress() {
        onPress.accept(this);
    }

    @Override
    public void playDownSound(SoundManager manager) {
        manager.play(SimpleSoundInstance.forUI(sound, pitch));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Ui.sprite(g, !active ? Ui.BUTTON_DISABLED : isHoveredOrFocused() ? Ui.BUTTON_HIGHLIGHTED : Ui.BUTTON,
                getX(), getY(), getWidth(), getHeight());
        Font font = Minecraft.getInstance().font;
        int color = active ? (isHoveredOrFocused() ? Ui.GOLD_LIGHT : Ui.CREAM) : 0xFFA89C8C;
        int textY = getY() + (getHeight() - font.lineHeight) / 2 + 1;
        if (icon != null) {
            int iconSize = getHeight() >= 18 ? 12 : 8;
            int contentWidth = iconSize + 3 + font.width(getMessage());
            int start = getX() + Math.max(4, (getWidth() - contentWidth) / 2);
            Ui.icon(g, icon, start, getY() + (getHeight() - iconSize) / 2, iconSize);
            Component text = Ui.fit(font, getMessage(), getWidth() - (start - getX()) - iconSize - 6);
            g.drawString(font, text, start + iconSize + 3, textY, color, true);
        } else {
            Component text = Ui.fit(font, getMessage(), getWidth() - 8);
            Ui.centered(g, font, text, getX() + getWidth() / 2, textY, color, true);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
