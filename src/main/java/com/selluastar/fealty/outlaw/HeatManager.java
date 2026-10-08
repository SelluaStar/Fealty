package com.selluastar.fealty.outlaw;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.event.WantedLevelEvent;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.entity.BountyHunterEntity;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.PlayerRepData;
import com.selluastar.fealty.rep.RepManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Wanted escalation. Time spent Hated by a village builds heat: first bounty hunters come after the player, then
 * the Tyrant Lord rides out. Rising back above Hated cools it down.
 */
public final class HeatManager {
    private HeatManager() {
    }

    public static int heat(ServerPlayer player, ResourceLocation faction) {
        return RepManager.data(player).heat().getOrDefault(faction, 0);
    }

    public static int wantedLevel(ServerPlayer player, ResourceLocation faction) {
        return RepManager.data(player).wanted().getOrDefault(faction, 0);
    }

    /** The player's highest wanted level with any faction. */
    public static int maxWantedLevel(ServerPlayer player) {
        int max = 0;
        for (int level : RepManager.data(player).wanted().values()) {
            max = Math.max(max, level);
        }
        return max;
    }

    public static void addHeat(ServerPlayer player, ResourceLocation faction, int amount) {
        setHeat(player, faction, heat(player, faction) + amount);
    }

    public static void setHeat(ServerPlayer player, ResourceLocation faction, int value) {
        PlayerRepData data = RepManager.data(player);
        int heat = Mth.clamp(value, 0, FealtyConfig.HEAT_MAX.get());
        if (heat == 0) {
            data.heat().remove(faction);
        } else {
            data.heat().put(faction, heat);
        }
        updateWanted(player, faction, heat);
        FealtyWorldData.get(player.server).setDirty();
    }

    public static void clearAll(ServerPlayer player) {
        PlayerRepData data = RepManager.data(player);
        for (ResourceLocation faction : new ArrayList<>(data.heat().keySet())) {
            setHeat(player, faction, 0);
        }
    }

    private static void updateWanted(ServerPlayer player, ResourceLocation faction, int heat) {
        int level = heat >= FealtyConfig.HEAT_TYRANT.get() ? WantedLevelEvent.TYRANT
                : heat >= FealtyConfig.HEAT_BOUNTY.get() ? WantedLevelEvent.BOUNTY : WantedLevelEvent.NOT_WANTED;
        PlayerRepData data = RepManager.data(player);
        int old = data.wanted().getOrDefault(faction, 0);
        if (old == level) {
            return;
        }
        if (level == 0) {
            data.wanted().remove(faction);
        } else {
            data.wanted().put(faction, level);
        }
        NeoForge.EVENT_BUS.post(new WantedLevelEvent(player, faction, old, level));
        Component name = Factions.displayName(player.server, faction);
        if (level > old) {
            player.sendSystemMessage(Component.translatable("fealty.wanted.level" + level, name).withStyle(ChatFormatting.DARK_RED));
            FealtyEvents.fire(player, FealtyEvents.WANTED);
            if (level == WantedLevelEvent.TYRANT) {
                TyrantEvent.begin(player, faction);
            }
        } else {
            player.sendSystemMessage(Component.translatable("fealty.wanted.cooled", name).withStyle(ChatFormatting.GRAY));
            if (level < WantedLevelEvent.TYRANT) {
                TyrantEvent.cancelFor(player, faction);
            }
        }
    }

    /** Once a minute: Hated factions build heat, others cool, and wanted players get bounty hunters. */
    public static void tickMinute(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        int hated = TierManager.hated().rank();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            PlayerRepData data = RepManager.data(player);
            List<ResourceLocation> factions = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Integer> entry : data.rep().entrySet()) {
                if (Factions.isVillage(entry.getKey())) {
                    factions.add(entry.getKey());
                }
            }
            for (ResourceLocation faction : data.heat().keySet()) {
                if (!factions.contains(faction)) {
                    factions.add(faction);
                }
            }
            ResourceLocation hottest = null;
            for (ResourceLocation faction : factions) {
                boolean isHated = RepManager.getTier(player, faction).rank() <= hated;
                int heat = heat(player, faction);
                if (isHated) {
                    setHeat(player, faction, heat + FealtyConfig.HEAT_PER_MINUTE.get());
                } else if (heat > 0) {
                    setHeat(player, faction, heat - FealtyConfig.HEAT_COOL_PER_MINUTE.get());
                }
                if (wantedLevel(player, faction) > 0 && (hottest == null || heat(player, faction) > heat(player, hottest))) {
                    hottest = faction;
                }
            }
            if (hottest != null && now >= data.nextBountyTime()) {
                int interval = FealtyConfig.BOUNTY_INTERVAL.get();
                if (TyrantEvent.isHunted(player)) {
                    interval /= 2;
                }
                data.setNextBountyTime(now + interval);
                dispatchHunters(player, hottest);
            }
        }
    }

    /** Send a party of bounty hunters after the player, a little way off. */
    public static void dispatchHunters(ServerPlayer player, ResourceLocation faction) {
        ServerLevel level = player.serverLevel();
        int count = 1 + wantedLevel(player, faction) + level.getRandom().nextInt(2);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            int distance = 24 + level.getRandom().nextInt(16);
            int x = player.getBlockX() + Mth.floor(Math.cos(angle) * distance);
            int z = player.getBlockZ() + Mth.floor(Math.sin(angle) * distance);
            // Near the player's height, so hunters reach players underground and never land on the Nether roof.
            java.util.Optional<BlockPos> spot = com.selluastar.fealty.util.SpawnSpots.nearY(level, x, z, player.getBlockY(), 12);
            if (spot.isEmpty()) {
                continue;
            }
            BlockPos pos = spot.get();
            BountyHunterEntity hunter = ModEntities.BOUNTY_HUNTER.get().create(level);
            if (hunter == null) {
                continue;
            }
            hunter.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0);
            hunter.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            hunter.hunt(player, level.getGameTime() + 12000, false);
            level.addFreshEntity(hunter);
            spawned++;
        }
        if (spawned > 0) {
            player.sendSystemMessage(Component.translatable("fealty.bounty.dispatched", Factions.displayName(player.server, faction))
                    .withStyle(ChatFormatting.RED));
        }
    }
}
