package com.selluastar.fealty.rep;

import java.util.Map;
import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.lordship.LordshipManager;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.outlaw.HeatManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runs once per in-game day (taxes, optional rep decay) and once per minute (heat). */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class DailyTicker {
    private static long lastDay = Long.MIN_VALUE;

    private DailyTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int ticks = server.getTickCount();
        if (ticks % 1200 == 0) {
            HeatManager.tickMinute(server);
        }
        if (ticks % 200 != 0) {
            return;
        }
        long day = RepManager.day(server);
        if (lastDay == Long.MIN_VALUE) {
            lastDay = day;
            return;
        }
        if (day != lastDay) {
            lastDay = day;
            onNewDay(server, day);
        }
    }

    private static void onNewDay(MinecraftServer server, long day) {
        LordshipManager.onNewDay(server, day);
        if (FealtyConfig.NEGATIVE_REP_DECAY.get()) {
            int heal = FealtyConfig.NEGATIVE_REP_DECAY_PER_DAY.get();
            FealtyWorldData world = FealtyWorldData.get(server);
            for (Map.Entry<UUID, PlayerRepData> entry : world.players().entrySet()) {
                for (Map.Entry<ResourceLocation, Integer> rep : entry.getValue().rep().entrySet()) {
                    if (rep.getValue() < 0) {
                        rep.setValue(Math.min(0, rep.getValue() + heal));
                    }
                }
            }
            world.setDirty();
            server.getPlayerList().getPlayers().forEach(FealtyNetwork::syncAll);
        }
    }
}
