package com.selluastar.fealty.rep;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.crime.Gossip;
import com.selluastar.fealty.lordship.LordshipManager;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.outlaw.HeatManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runs once per Fealty day (taxes, fading standing, optional rep decay) and once per minute (heat). */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class DailyTicker {
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
        int advanced = FealtyCalendar.tick(server);
        if (advanced > 0) {
            onNewDay(server, FealtyCalendar.day(server), advanced);
        }
    }

    private static void onNewDay(MinecraftServer server, long day, int advanced) {
        if (FealtyConfig.NEGLECT.get()) {
            neglect(server, day, advanced);
        }
        LordshipManager.onNewDay(server, day);
        com.selluastar.fealty.war.Strongholds.get(server).tickDay(server, day);
        com.selluastar.fealty.war.Campaigns.tickDay(server, day);
        Gossip.spread(server);
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

    /** Total rep a village's standing has faded after {@code away} days without a visit. */
    public static int neglectAfter(long away) {
        long grace = FealtyConfig.NEGLECT_GRACE_DAYS.get();
        if (away < grace) {
            return 0;
        }
        return (int) Math.min(100000, FealtyConfig.NEGLECT_FIRST.get() + FealtyConfig.NEGLECT_DAILY.get() * (away - grace));
    }

    /**
     * Standing fades with villages a player stays away from: nothing for the first days, then a larger drop, then a
     * little each day after, down to the floor at worst. Villages never visited since this was added count as
     * visited today.
     */
    private static void neglect(MinecraftServer server, long day, int advanced) {
        int floor = FealtyConfig.NEGLECT_FLOOR.get();
        FealtyWorldData world = FealtyWorldData.get(server);
        for (Map.Entry<UUID, PlayerRepData> entry : world.players().entrySet()) {
            PlayerRepData data = entry.getValue();
            for (Map.Entry<ResourceLocation, Integer> rep : new ArrayList<>(data.rep().entrySet())) {
                ResourceLocation faction = rep.getKey();
                if (!Factions.isVillage(faction) || rep.getValue() <= floor) {
                    continue;
                }
                Long last = data.visited().get(faction);
                if (last == null) {
                    data.visited().put(faction, day);
                    continue;
                }
                long away = day - last;
                int due = neglectAfter(away) - neglectAfter(Math.max(0, away - advanced));
                if (due > 0) {
                    RepManager.fade(server, entry.getKey(), faction, due, floor);
                }
            }
        }
        world.setDirty();
    }
}
