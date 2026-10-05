package com.selluastar.fealty.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/** Human NPCs (bandits, hunters, the fence, the Tyrant Lord) with player-format skins, armour and held items. */
public class FealtyHumanoidRenderer<T extends Mob> extends HumanoidMobRenderer<T, PlayerModel<T>> {
    private final ResourceLocation texture;
    private final float scale;

    public FealtyHumanoidRenderer(EntityRendererProvider.Context context, ResourceLocation texture, float scale) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F * scale);
        this.texture = texture;
        this.scale = scale;
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Override
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        PlayerModel<T> model = getModel();
        HumanoidModel.ArmPose main = pose(entity, entity.getItemInHand(InteractionHand.MAIN_HAND));
        HumanoidModel.ArmPose off = entity.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        if (entity.getMainArm() == HumanoidArm.RIGHT) {
            model.rightArmPose = main;
            model.leftArmPose = off;
        } else {
            model.leftArmPose = main;
            model.rightArmPose = off;
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static HumanoidModel.ArmPose pose(Mob entity, ItemStack stack) {
        if (stack.isEmpty()) {
            return HumanoidModel.ArmPose.EMPTY;
        }
        if (entity.isAggressive() && stack.getItem() instanceof BowItem) {
            return HumanoidModel.ArmPose.BOW_AND_ARROW;
        }
        if (stack.getItem() instanceof CrossbowItem) {
            return entity.isAggressive() ? HumanoidModel.ArmPose.CROSSBOW_HOLD : HumanoidModel.ArmPose.ITEM;
        }
        return HumanoidModel.ArmPose.ITEM;
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375F * scale, 0.9375F * scale, 0.9375F * scale);
    }
}
