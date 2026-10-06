package com.selluastar.fealty.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.block.MailboxBlock;
import com.selluastar.fealty.block.MailboxBlockEntity;
import com.selluastar.fealty.client.hud.MailLayer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** The mailbox's flag: up while you have letters waiting, down otherwise. */
public class MailboxRenderer implements BlockEntityRenderer<MailboxBlockEntity> {
    private static final ResourceLocation FLAG = Fealty.id("textures/block/mailbox_flag.png");

    public MailboxRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MailboxBlockEntity mailbox, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = mailbox.getBlockState().getValue(MailboxBlock.FACING);
        boolean up = MailLayer.unread() > 0;
        pose.pushPose();
        // Turn like the block model does for its facing (north is the model's own orientation).
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-(facing.toYRot() + 180.0F)));
        pose.translate(-0.5, 0.0, -0.5);
        // The hinge on the east side of the box.
        pose.translate(13.0 / 16.0, 13.0 / 16.0, 9.0 / 16.0);
        if (!up) {
            pose.mulPose(Axis.XN.rotationDegrees(90.0F));
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FLAG));
        box(consumer, pose, 0.0F, -0.5F, -0.5F, 1.0F, 6.0F, 0.5F, light);
        box(consumer, pose, 0.0F, 3.0F, -4.0F, 1.0F, 6.0F, -0.5F, light);
        pose.popPose();
    }

    /** A cuboid in pixels, with the whole texture on every face. */
    private static void box(VertexConsumer c, PoseStack pose, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        float a = x0 / 16, b = y0 / 16, d = z0 / 16, e = x1 / 16, f = y1 / 16, h = z1 / 16;
        PoseStack.Pose p = pose.last();
        quad(c, p, light, 0, 0, -1, a, b, d, a, f, d, e, f, d, e, b, d);
        quad(c, p, light, 0, 0, 1, e, b, h, e, f, h, a, f, h, a, b, h);
        quad(c, p, light, -1, 0, 0, a, b, h, a, f, h, a, f, d, a, b, d);
        quad(c, p, light, 1, 0, 0, e, b, d, e, f, d, e, f, h, e, b, h);
        quad(c, p, light, 0, 1, 0, a, f, d, a, f, h, e, f, h, e, f, d);
        quad(c, p, light, 0, -1, 0, a, b, h, a, b, d, e, b, d, e, b, h);
    }

    private static void quad(VertexConsumer c, PoseStack.Pose p, int light, float nx, float ny, float nz,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vertex(c, p, light, nx, ny, nz, x1, y1, z1, 0.0F, 1.0F);
        vertex(c, p, light, nx, ny, nz, x2, y2, z2, 0.0F, 0.0F);
        vertex(c, p, light, nx, ny, nz, x3, y3, z3, 1.0F, 0.0F);
        vertex(c, p, light, nx, ny, nz, x4, y4, z4, 1.0F, 1.0F);
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose p, int light, float nx, float ny, float nz, float x, float y, float z,
                               float u, float v) {
        c.addVertex(p, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(p, nx, ny, nz);
    }
}
