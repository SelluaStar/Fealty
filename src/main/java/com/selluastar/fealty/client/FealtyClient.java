package com.selluastar.fealty.client;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.client.hud.AnnouncementLayer;
import com.selluastar.fealty.client.hud.ClientFeedback;
import com.selluastar.fealty.client.hud.MailLayer;
import com.selluastar.fealty.client.hud.QuestTrackerLayer;
import com.selluastar.fealty.client.hud.RepFeedLayer;
import com.selluastar.fealty.client.hud.RetinueLayer;
import com.selluastar.fealty.client.render.FealtyHumanoidRenderer;
import com.selluastar.fealty.client.render.GuardRenderer;
import com.selluastar.fealty.client.render.MailboxRenderer;
import com.selluastar.fealty.client.screen.MailWriteScreen;
import com.selluastar.fealty.client.render.RobedVillagerRenderer;
import com.selluastar.fealty.registry.ModBlockEntities;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.registry.ModMenus;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** Client setup: entity renderers, HUD layers and cleanup on disconnect. */
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
            event.registerEntityRenderer(ModEntities.VILLAGE_GUARD.get(), GuardRenderer::new);
            event.registerBlockEntityRenderer(ModBlockEntities.MAILBOX.get(), MailboxRenderer::new);
            event.registerEntityRenderer(ModEntities.SMOKE_BOMB.get(), ThrownItemRenderer::new);
        }

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenus.MAIL_WRITE.get(), MailWriteScreen::new);
        }

        @SubscribeEvent
        public static void registerGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Fealty.id("quest_tracker"), QuestTrackerLayer::render);
            event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Fealty.id("rep_feed"), RepFeedLayer::render);
            event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Fealty.id("retinue"), RetinueLayer::render);
            event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Fealty.id("mail"), MailLayer::render);
            event.registerAbove(VanillaGuiLayers.TITLE, Fealty.id("announcement"), AnnouncementLayer::render);
        }
    }

    @EventBusSubscriber(modid = Fealty.MOD_ID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientRepCache.clear();
            ClientQuestCache.clear();
            ClientFeedback.clear();
            com.selluastar.fealty.client.bubble.SpeechBubbles.clear();
            com.selluastar.fealty.client.bubble.QuestMarkers.clear();
            RetinueLayer.clear();
            MailLayer.clear();
        }
    }
}
