package com.selluastar.fealty.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.client.ui.FealtyButton;
import com.selluastar.fealty.client.ui.Ui;
import com.selluastar.fealty.network.LockpickAttemptPayload;
import com.selluastar.fealty.network.LockpickResultPayload;
import com.selluastar.fealty.network.OpenLockpickPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Picking a lock. Drag the knob around the ring to set the pick, then try the lock: it turns further the closer the
 * pick is to the spot where it gives, and opens when it is right. Every miss strains the pick, and someone may hear
 * it. Each try leaves a mark on the ring, coloured by how far the lock turned.
 */
public class LockpickScreen extends Screen {
    private static final ResourceLocation TEXTURE = Fealty.id("textures/gui/lockpick.png");
    private static final int WIDTH = 230;
    private static final int HEIGHT = 272;
    private static final int LOCK_Y = 138;
    /** Radius of the slider ring around the pick's pivot (the keyhole). */
    private static final int RING = 90;
    private static final int RING_DARK = 0xFF2B2018;
    private static final int RING_GROOVE = 0xFF5C4630;
    private static final int RING_GROOVE_LIT = 0xFF7A5E3E;
    private static final int TICK = 0xFF8C7556;
    private static final int MARK_STUCK = 0xFFB71C1C;
    private static final int MARK_COLD = 0xFFE07B39;
    private static final int MARK_WARM = 0xFFE8C547;
    private static final int MARK_CLOSE = 0xFF6BCB5B;
    private static final int MAX_MARKS = 12;

    private final OpenLockpickPayload data;
    private final List<float[]> marks = new ArrayList<>();
    private int left;
    private int top;
    private float pick = 90F;
    private float triedAt;
    private boolean dragging;
    private int lastTickStep = -1;
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
        return top + LOCK_Y;
    }

    /** The pick turns about the keyhole, a little above the middle of the cylinder. */
    private int pivotY() {
        return centerY() - 6;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        tryButton = addRenderableWidget(new FealtyButton(left + 20, top + HEIGHT - 30, 110, 20, Component.translatable("fealty.lockpick.try"),
                b -> attempt()).icon("lock"));
        addRenderableWidget(new FealtyButton(left + WIDTH - 90, top + HEIGHT - 30, 70, 20, Component.translatable("fealty.lockpick.leave"),
                b -> onClose()).icon("door"));
    }

    private boolean ready() {
        return !waiting && !opened && closeIn < 0 && holdTicks == 0 && Math.abs(cylinder) < 4F;
    }

    /** The pick can only be moved while the lock is at rest. */
    private boolean canMove() {
        return !waiting && !opened && closeIn < 0 && holdTicks == 0;
    }

    private void attempt() {
        if (!ready()) {
            return;
        }
        dragging = false;
        waiting = true;
        waitingTicks = 0;
        triedAt = pick;
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
        if (!result.opened()) {
            marks.add(new float[] {triedAt, result.turn()});
            if (marks.size() > MAX_MARKS) {
                marks.removeFirst();
            }
        }
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

    // ---- The ring ----

    private float knobX() {
        return centerX() + RING * Mth.cos((180F + pick) * Mth.DEG_TO_RAD);
    }

    private float knobY() {
        return pivotY() + RING * Mth.sin((180F + pick) * Mth.DEG_TO_RAD);
    }

    private boolean onKnob(double mouseX, double mouseY) {
        double dx = mouseX - knobX();
        double dy = mouseY - knobY();
        return dx * dx + dy * dy <= 11 * 11;
    }

    /** Whether the mouse is on the ring's upper half (with a little give below its ends). */
    private boolean onRing(double mouseX, double mouseY) {
        double dx = mouseX - centerX();
        double dy = mouseY - pivotY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        return Math.abs(distance - RING) <= 10 && dy <= 10;
    }

    /** Point the pick at the mouse. Below the ring's ends it stays at the nearer end. */
    private void pickFromMouse(double mouseX, double mouseY) {
        double angle = Math.toDegrees(Math.atan2(mouseY - pivotY(), mouseX - centerX()));
        float next = angle <= 0 ? (float) (angle + 180) : angle > 90 ? 0F : 180F;
        setPick(next);
    }

    private void setPick(float next) {
        pick = Mth.clamp(next, 0F, 180F);
        // A faint click every few degrees, like the pins brushing past.
        int step = (int) (pick / 6F);
        if (step != lastTickStep && minecraft != null) {
            if (lastTickStep >= 0) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIPWIRE_CLICK_ON, 1.7F + (pick / 180F) * 0.3F, 0.25F));
            }
            lastTickStep = step;
        }
    }

    private void nudge(float degrees) {
        if (canMove()) {
            setPick(pick + degrees);
        }
    }

    // ---- Drawing ----

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        Ui.window(g, left, top, WIDTH, HEIGHT);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Ui.icon(g, "lock", left + 12, top + 9, 16);
        g.drawString(font, title, left + 32, top + 10, Ui.INK, false);
        Ui.scaled(g, font, Component.translatable("fealty.lockpick.uses", usesLeft, spare), left + 32, top + 21, 0.75F, Ui.FADED, false);

        int cx = centerX();
        int cy = centerY();
        boolean hot = dragging || (canMove() && (onKnob(mouseX, mouseY) || onRing(mouseX, mouseY)));
        drawRing(g, cx, pivotY(), hot);

        float turned = Mth.lerp(partialTick, prevCylinder, cylinder);
        // A pick straining against a stuck lock shakes.
        float shake = holdTicks > 0 && !opened ? (float) Math.sin((holdTicks + partialTick) * 2.6F) * 2.5F : 0F;
        // a soft shadow under the plate, then the plate
        RenderSystem.enableBlend();
        g.setColor(0F, 0F, 0F, 0.35F);
        g.blit(TEXTURE, cx - 54, cy - 53, 0, 0, 112, 112, 256, 128);
        g.setColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
        g.blit(TEXTURE, cx - 56, cy - 56, 0, 0, 112, 112, 256, 128);

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(turned));
        g.blit(TEXTURE, -32, -32, 128, 0, 64, 64, 256, 128);
        // the tension wrench sits in the bottom of the keyhole and turns with the cylinder
        g.pose().translate(0, 10, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(90F));
        g.blit(TEXTURE, 0, -4, 96, 112, 48, 8, 256, 128);
        g.pose().popPose();

        g.pose().pushPose();
        g.pose().translate(cx, pivotY(), 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(180F + pick + shake));
        g.blit(TEXTURE, -2, -5, 0, 112, 84, 10, 256, 128);
        g.pose().popPose();

        g.pose().pushPose();
        g.pose().translate(knobX(), knobY(), 0);
        g.blit(TEXTURE, -9, -9, hot ? 212 : 192, 0, 18, 18, 256, 128);
        g.pose().popPose();

        int textY = cy + 62;
        if (message.getString().isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(left + 14, textY, 0);
            g.pose().scale(0.8F, 0.8F, 1F);
            Ui.wrapped(g, font, Component.translatable("fealty.lockpick.hint"), 0, 0, (int) ((WIDTH - 28) / 0.8F), Ui.FADED, 5);
            g.pose().popPose();
        } else {
            Ui.centered(g, font, message, cx, textY, messageColor, false);
            if (!heard.getString().isEmpty()) {
                Ui.centered(g, font, heard, cx, textY + 12, Ui.RED, false);
            }
        }
    }

    /** The slider: a groove on the upper half of a circle round the pick, with ticks and the marks of past tries. */
    private void drawRing(GuiGraphics g, int cx, int cy, boolean hot) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        for (int degree = 0; degree <= 180; degree++) {
            g.pose().pushPose();
            g.pose().mulPose(Axis.ZP.rotationDegrees(180F + degree));
            g.fill(RING - 5, -1, RING + 5, 1, RING_DARK);
            g.pose().popPose();
        }
        for (int degree = 0; degree <= 180; degree++) {
            g.pose().pushPose();
            g.pose().mulPose(Axis.ZP.rotationDegrees(180F + degree));
            g.fill(RING - 3, -1, RING + 3, 1, hot ? RING_GROOVE_LIT : RING_GROOVE);
            g.pose().popPose();
        }
        for (int degree = 0; degree <= 180; degree += 15) {
            g.pose().pushPose();
            g.pose().mulPose(Axis.ZP.rotationDegrees(180F + degree));
            int length = degree % 90 == 0 ? 6 : degree % 45 == 0 ? 4 : 2;
            g.fill(RING + 6, 0, RING + 6 + length, 1, TICK);
            g.pose().popPose();
        }
        for (float[] mark : marks) {
            g.pose().pushPose();
            g.pose().mulPose(Axis.ZP.rotationDegrees(180F + mark[0]));
            float turn = mark[1];
            int color = turn >= 0.75F ? MARK_CLOSE : turn >= 0.4F ? MARK_WARM : turn > 0F ? MARK_COLD : MARK_STUCK;
            g.fill(RING - 2, -1, RING + 2, 2, color);
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && (onKnob(mouseX, mouseY) || onRing(mouseX, mouseY))) {
            if (canMove()) {
                dragging = true;
                pickFromMouse(mouseX, mouseY);
            }
            return true;
        }
        double dx = mouseX - centerX();
        double dy = mouseY - centerY();
        if (button == 0 && dx * dx + dy * dy <= 56 * 56) {
            attempt();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            if (canMove()) {
                pickFromMouse(mouseX, mouseY);
            } else {
                dragging = false;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == 0) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            nudge((float) Math.signum(scrollY) * (hasShiftDown() ? 5F : 1F));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
            attempt();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_A) {
            nudge(hasShiftDown() ? -5F : -1F);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_D) {
            nudge(hasShiftDown() ? 5F : 1F);
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
