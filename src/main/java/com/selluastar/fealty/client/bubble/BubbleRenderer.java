package com.selluastar.fealty.client.bubble;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.config.FealtyClientConfig;
import com.selluastar.fealty.network.QuestMarkersPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/** Draws speech bubbles and quest markers (! and ?) above NPCs' heads. */
@EventBusSubscriber(modid = Fealty.MOD_ID, value = Dist.CLIENT)
public final class BubbleRenderer {
    private static final int MAX_WIDTH = 150;
    private static final double MAX_DISTANCE = 28.0;
    private static final int FILL = 0xF4EAD0;
    private static final int BORDER = 0x3B2A1A;
    private static final int INK = 0x3B2A1A;

    private BubbleRenderer() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        SpeechBubbles.tick();
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || entity == minecraft.player || entity.isInvisible()) {
            return;
        }
        SpeechBubbles.Bubble bubble = FealtyClientConfig.SPEECH_BUBBLES.get() ? SpeechBubbles.get(entity.getId()) : null;
        QuestMarkersPayload.Kind marker = FealtyClientConfig.QUEST_MARKERS.get() ? QuestMarkers.get(entity.getId()) : null;
        if (bubble == null && marker == null) {
            return;
        }
        if (minecraft.getEntityRenderDispatcher().distanceToSqr(entity) > MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        float height = entity.getBbHeight() + 0.55F + (entity.shouldShowName() ? 0.3F : 0.0F);
        pose.pushPose();
        pose.translate(0.0, height, 0.0);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.025F, -0.025F, 0.025F);
        if (bubble != null) {
            drawBubble(minecraft.font, pose, event.getMultiBufferSource(), event.getPackedLight(), bubble, event.getPartialTick());
        } else {
            drawMarker(minecraft.font, pose, event.getMultiBufferSource(), marker, entity.tickCount + event.getPartialTick());
        }
        pose.popPose();
    }

    private static void drawBubble(Font font, PoseStack pose, MultiBufferSource buffers, int light, SpeechBubbles.Bubble bubble, float partialTick) {
        float age = bubble.age(SpeechBubbles.now(), partialTick);
        float alpha = Mth.clamp(Math.min(age / 4.0F, (bubble.ticks() - age) / 10.0F), 0.0F, 1.0F);
        if (alpha <= 0.02F) {
            return;
        }
        List<FormattedCharSequence> lines = font.split(bubble.text(), MAX_WIDTH);
        int textWidth = 0;
        for (FormattedCharSequence line : lines) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        int w = textWidth + 10;
        int h = lines.size() * 10 + 6;
        float x0 = -w / 2.0F;
        float y0 = -h - 6;
        Matrix4f matrix = pose.last().pose();
        VertexConsumer background = buffers.getBuffer(RenderType.textBackground());
        int border = argb(BORDER, alpha * 0.95F);
        int fill = argb(FILL, alpha * 0.92F);
        quad(background, matrix, x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, 0.03F, border, light);
        quad(background, matrix, x0, y0, x0 + w, y0 + h, 0.02F, fill, light);
        // tail
        triangle(background, matrix, -4, y0 + h, 4, y0 + h, 0, y0 + h + 5, 0.02F, fill, light);
        int color = argb(INK, Math.max(alpha, 0.1F));
        float y = y0 + 4;
        for (FormattedCharSequence line : lines) {
            font.drawInBatch(line, -font.width(line) / 2.0F, y, color, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, light);
            y += 10;
        }
    }

    private static void drawMarker(Font font, PoseStack pose, MultiBufferSource buffers, QuestMarkersPayload.Kind kind, float time) {
        String symbol = switch (kind) {
            case OFFER, FAVOR, TARGET -> "!";
            case ACTIVE, READY -> "?";
            case LETTER -> "✉";
        };
        int color = switch (kind) {
            case OFFER, READY -> 0xF2C230;
            case FAVOR -> 0xF4EAD0;
            case ACTIVE -> 0x9E9E9E;
            case LETTER, TARGET -> 0x90CAF9;
        };
        float bob = Mth.sin(time * 0.12F) * 1.5F;
        pose.pushPose();
        pose.translate(0.0F, -6.0F + bob, 0.0F);
        float scale = kind == QuestMarkersPayload.Kind.FAVOR ? 1.6F : 2.4F;
        pose.scale(scale, scale, scale);
        Matrix4f matrix = pose.last().pose();
        Component text = Component.literal(symbol);
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(text, x, -8, 0x50000000 | color, false, matrix, buffers, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        font.drawInBatch(text, x, -8, 0xFF000000 | color, true, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
    }

    private static int argb(int rgb, float alpha) {
        int a = Mth.clamp((int) (alpha * 255), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float x1, float y1, float z, int color, int light) {
        consumer.addVertex(matrix, x0, y0, z).setColor(color).setLight(light);
        consumer.addVertex(matrix, x0, y1, z).setColor(color).setLight(light);
        consumer.addVertex(matrix, x1, y1, z).setColor(color).setLight(light);
        consumer.addVertex(matrix, x1, y0, z).setColor(color).setLight(light);
    }

    private static void triangle(VertexConsumer consumer, Matrix4f matrix, float ax, float ay, float bx, float by, float cx, float cy,
                                 float z, int color, int light) {
        consumer.addVertex(matrix, ax, ay, z).setColor(color).setLight(light);
        consumer.addVertex(matrix, cx, cy, z).setColor(color).setLight(light);
        consumer.addVertex(matrix, cx, cy, z).setColor(color).setLight(light);
        consumer.addVertex(matrix, bx, by, z).setColor(color).setLight(light);
    }
}
