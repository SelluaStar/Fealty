package com.selluastar.fealty.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.entity.VillageGuardEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Village guards: a skin for each rank, and a tabard in their village's colour. */
public class GuardRenderer extends FealtyHumanoidRenderer<VillageGuardEntity> {
    private static final ResourceLocation[] SKINS = {
            Fealty.id("textures/entity/guard/swordsman.png"),
            Fealty.id("textures/entity/guard/archer.png"),
            Fealty.id("textures/entity/guard/sergeant.png")
    };
    private static final ResourceLocation TABARD = Fealty.id("textures/entity/guard/tabard.png");

    public GuardRenderer(EntityRendererProvider.Context context) {
        super(context, SKINS[0], 1.0F);
        addLayer(new TabardLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(VillageGuardEntity entity) {
        return SKINS[entity.rank().ordinal() % SKINS.length];
    }

    /** The tabard is drawn white in its own texture and tinted with the village's colour. */
    private static final class TabardLayer extends RenderLayer<VillageGuardEntity, PlayerModel<VillageGuardEntity>> {
        TabardLayer(RenderLayerParent<VillageGuardEntity, PlayerModel<VillageGuardEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, VillageGuardEntity entity, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!entity.isInvisible()) {
                renderColoredCutoutModel(getParentModel(), TABARD, poseStack, buffer, packedLight, entity, 0xFF000000 | entity.color());
            }
        }
    }
}
