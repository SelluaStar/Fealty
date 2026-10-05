package com.selluastar.fealty.client;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.client.render.FealtyHumanoidRenderer;
import com.selluastar.fealty.client.render.RobedVillagerRenderer;
import com.selluastar.fealty.registry.ModEntities;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client setup: entity renderers and cleanup on disconnect. */
public final class FealtyClient {
    private FealtyClient() {
    }

    @EventBusSubscriber(modid = Fealty.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.VILLAGE_ELDER.get(),
                    ctx -> new RobedVillagerRenderer<>(ctx, Fealty.id("textures/entity/village_elder.png")));
            event.registerEntityRenderer(ModEntities.KEEPER.get(),
                    ctx -> new RobedVillagerRenderer<>(ctx, Fealty.id("textures/entity/keeper.png")));
            event.registerEntityRenderer(ModEntities.GUILD_FENCE.get(),
                    ctx -> new FealtyHumanoidRenderer<>(ctx, Fealty.id("textures/entity/guild_fence.png"), 1.0F));
            event.registerEntityRenderer(ModEntities.BLACK_MARKETEER.get(),
                    ctx -> new FealtyHumanoidRenderer<>(ctx, Fealty.id("textures/entity/black_marketeer.png"), 1.0F));
            event.registerEntityRenderer(ModEntities.BANDIT.get(),
                    ctx -> new FealtyHumanoidRenderer<>(ctx, Fealty.id("textures/entity/bandit.png"), 1.0F));
            event.registerEntityRenderer(ModEntities.BANDIT_CAPTAIN.get(),
                    ctx -> new FealtyHumanoidRenderer<>(ctx, Fealty.id("textures/entity/bandit_captain.png"), 1.05F));
            event.registerEntityRenderer(ModEntities.BOUNTY_HUNTER.get(),
                    ctx -> new FealtyHumanoidRenderer<>(ctx, Fealty.id("textures/entity/bounty_hunter.png"), 1.0F));
            event.registerEntityRenderer(ModEntities.TYRANT_LORD.get(),
                    ctx -> new FealtyHumanoidRenderer<>(ctx, Fealty.id("textures/entity/tyrant_lord.png"), 1.4F));
            event.registerEntityRenderer(ModEntities.SMOKE_BOMB.get(), ThrownItemRenderer::new);
        }
    }

    @EventBusSubscriber(modid = Fealty.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientRepCache.clear();
        }
    }
}
