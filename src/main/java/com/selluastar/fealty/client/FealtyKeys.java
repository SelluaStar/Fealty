package com.selluastar.fealty.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.client.screen.JournalScreen;
import com.selluastar.fealty.config.FealtyClientConfig;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/** Fealty's key bindings, listed under "Fealty" in Options → Controls → Key Binds. */
public final class FealtyKeys {
    public static final String CATEGORY = "key.categories.fealty";

    public static final KeyMapping JOURNAL = new KeyMapping("key.fealty.journal", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping TOGGLE_TRACKER = new KeyMapping("key.fealty.toggle_tracker", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);
    public static final KeyMapping TRACK_NEXT = new KeyMapping("key.fealty.track_next", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, CATEGORY);

    private FealtyKeys() {
    }

    @EventBusSubscriber(modid = Fealty.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(JOURNAL);
            event.register(TOGGLE_TRACKER);
            event.register(TRACK_NEXT);
        }
    }

    @EventBusSubscriber(modid = Fealty.MOD_ID, value = Dist.CLIENT)
    public static final class Handler {
        private Handler() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) {
                return;
            }
            while (JOURNAL.consumeClick()) {
                if (minecraft.screen == null) {
                    minecraft.setScreen(new JournalScreen(JournalScreen.Page.QUESTS));
                }
            }
            while (TOGGLE_TRACKER.consumeClick()) {
                FealtyClientConfig.TrackerMode mode = FealtyClientConfig.TRACKER_MODE.get().next();
                FealtyClientConfig.setTrackerMode(mode);
                minecraft.player.displayClientMessage(Component.translatable("fealty.tracker.mode",
                        Component.translatable("fealty.tracker.mode." + mode.name().toLowerCase(java.util.Locale.ROOT))), true);
            }
            while (TRACK_NEXT.consumeClick()) {
                ClientQuestCache.trackNext();
            }
        }
    }
}
