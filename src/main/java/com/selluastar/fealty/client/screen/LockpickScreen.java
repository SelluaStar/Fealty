package com.selluastar.fealty.client.screen;

import com.mojang.math.Axis;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.client.ui.FealtyButton;
import com.selluastar.fealty.client.ui.Ui;
import com.selluastar.fealty.network.LockpickAttemptPayload;
import com.selluastar.fealty.network.LockpickResultPayload;
import com.selluastar.fealty.network.OpenLockpickPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Picking a lock. Move the mouse to set the pick, then try the lock: it turns further the closer the pick is to the
 * spot where it gives, and opens when it is right. Every miss strains the pick, and someone may hear it.
 */
public class LockpickScreen extends Screen {
    private static final ResourceLocation TEXTURE = Fealty.id("textures/gui/lockpick.png");
    private static final int WIDTH = 220;
    private static final int HEIGHT = 230;
    private static final int LOCK_Y = 44;
    private static final int STEEL = 0xFFB0B7BF;
    private static final int STEEL_DARK = 0xFF3A3F45;
    private static final int GRIP = 0xFF6D4C2F;

    private final OpenLockpickPayload data;
    private int left;
    private int top;
    private float pick = 90F;
    private float cylinder;
    private float prevCylinder;
    private float turnTo;
    private int holdTicks;
    private boolean waiting;
    private int waitingTicks;
    private boolean opened;
    private int closeIn = -1;
    private int usesLeft;
    private int spare;
    private Component message = Component.empty();
    private int messageColor = Ui.FADED;
    private Component heard = Component.empty();
    private FealtyButton tryButton;

    public LockpickScreen(OpenLockpickPayload data) {
        super(Component.translatable(data.coffer() ? "fealty.lockpick.title.coffer" : "fealty.lockpick.title"));
        this.data = data;
        this.usesLeft = data.usesLeft();
        this.spare = data.spare();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int centerX() {
        return left + WIDTH / 2;
    }

    private int centerY() {
        return top + LOCK_Y + 56;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        tryButton = addRenderableWidget(new FealtyButton(left + 20, top + HEIGHT - 30, 100, 20, Component.translatable("fealty.lockpick.try"),
                b -> attempt()).icon("lock"));
        addRenderableWidget(new FealtyButton(left + WIDTH - 90, top + HEIGHT - 30, 70, 20, Component.translatable("fealty.lockpick.leave"),
                b -> onClose()).icon("door"));
    }

    private boolean ready() {
        return !waiting && !opened && closeIn < 0 && holdTicks == 0 && Math.abs(cylinder) < 4F;
    }

    private void attempt() {
        if (!ready()) {
            return;
        }
        waiting = true;
        waitingTicks = 0;
        PacketDistributor.sendToServer(new LockpickAttemptPayload(data.pos(), pick));
    }

    /** The server's answer to a try. */
    public void result(LockpickResultPayload result) {
        waiting = false;
        if (result.closed()) {
            onClose();
            return;
        }
        usesLeft = result.usesLeft();
        spare = result.spare();
        turnTo = result.turn() * 90F;
        holdTicks = 10;
        heard = result.heard() ? Component.translatable("fealty.lockpick.heard") : Component.empty();
        if (result.opened()) {
            opened = true;
            message = Component.translatable("fealty.lockpick.opened");
            messageColor = Ui.GREEN;
        } else if (result.broke()) {
            message = Component.translatable(spare > 0 ? "fealty.lockpick.broke_spare" : "fealty.lockpick.broke");
            messageColor = Ui.RED;
            closeIn = 40;
        } else {
            float turn = result.turn();
            message = Component.translatable(turn >= 0.75F ? "fealty.lockpick.close" : turn >= 0.4F ? "fealty.lockpick.warm"
                    : turn > 0F ? "fealty.lockpick.cold" : "fealty.lockpick.stuck");
            messageColor = turn >= 0.75F ? Ui.GREEN : Ui.INK;
        }
    }

    @Override
    public void tick() {
        prevCylinder = cylinder;
        if (closeIn > 0 && --closeIn == 0) {
            onClose();
            return;
        }
        if (waiting && ++waitingTicks > 40) {
            waiting = false; // no answer: let the player try again
        }
        if (opened) {
            cylinder += (90F - cylinder) * 0.4F;
        } else if (holdTicks > 0) {
            holdTicks--;
            cylinder += (turnTo - cylinder) * 0.45F;
        } else {
            cylinder += -cylinder * 0.35F;
        }
        if (tryButton != null) {
            tryButton.active = ready();
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Ui.window(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!waiting && !opened && holdTicks == 0) {
            pick = Mth.clamp(mouseX - (centerX() - 90F), 0F, 180F);
        }
        super.render(g, mouseX, mouseY, partialTick);
        Ui.icon(g, "lock", left + 12, top + 9, 16);
        g.drawString(font, title, left + 32, top + 13, Ui.INK, false);
        Ui.scaled(g, font, Component.translatable("fealty.lockpick.uses", usesLeft, spare), left + 32, top + 25, 0.75F, Ui.FADED, false);

        int cx = centerX();
        int cy = centerY();
        float turned = Mth.lerp(partialTick, prevCylinder, cylinder);
        // A pick straining against a stuck lock shakes.
        float shake = holdTicks > 0 && !opened ? (float) Math.sin((holdTicks + partialTick) * 2.6F) * 2.5F : 0F;
        g.blit(TEXTURE, cx - 56, cy - 56, 0, 0, 112, 112, 256, 128);

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(turned));
        g.blit(TEXTURE, -32, -32, 128, 0, 64, 64, 256, 128);
        // the tension wrench turns with the cylinder
        g.fill(-2, 8, 2, 46, STEEL_DARK);
        g.fill(-1, 9, 1, 45, STEEL);
        g.fill(-2, 42, 22, 46, STEEL_DARK);
        g.fill(-1, 43, 21, 45, STEEL);
        g.pose().popPose();

        g.pose().pushPose();
        g.pose().translate(cx, cy - 6, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(180F + pick + shake));
        g.fill(2, -2, 70, 2, STEEL_DARK);
        g.fill(3, -1, 69, 1, STEEL);
        g.fill(2, -4, 6, 0, STEEL_DARK);
        g.fill(50, -3, 76, 3, GRIP);
        g.pose().popPose();

        int textY = top + LOCK_Y + 120;
        if (message.getString().isEmpty()) {
            Ui.wrapped(g, font, Component.translatable("fealty.lockpick.hint"), left + 14, textY, WIDTH - 28, Ui.FADED, 4);
        } else {
            Ui.centered(g, font, message, cx, textY, messageColor, false);
            if (!heard.getString().isEmpty()) {
                Ui.centered(g, font, heard, cx, textY + 12, Ui.RED, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double dx = mouseX - centerX();
        double dy = mouseY - centerY();
        if (button == 0 && dx * dx + dy * dy <= 56 * 56) {
            attempt();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 32) {
            attempt();
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
