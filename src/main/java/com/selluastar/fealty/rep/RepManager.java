package com.selluastar.fealty.rep;

import java.util.UUID;

import com.selluastar.fealty.api.RepSource;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.event.RepChangeEvent;
import com.selluastar.fealty.api.event.TierChangedEvent;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.data.SourceSettings;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.registry.FealtyRegistries;
import com.selluastar.fealty.registry.ModCriteria;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.NeoForge;

/** All reputation reads and writes go through here so events, Renown, sync and feedback stay consistent. */
public final class RepManager {
    private RepManager() {
    }

    // ---- Scale ----

    public static int min() {
        return FealtyConfig.REP_MIN.get();
    }

    public static int max() {
        return FealtyConfig.REP_MAX.get();
    }

    public static int clamp(int rep) {
        return Mth.clamp(rep, min(), max());
    }

    public static RepTier tierOf(int rep) {
        return TierManager.tierFor(rep);
    }

    public static long day(MinecraftServer server) {
        return server.overworld().getDayTime() / 24000L;
    }

    // ---- Reads ----

    public static PlayerRepData data(ServerPlayer player) {
        return FealtyWorldData.get(player.server).player(player.getUUID());
    }

    public static int getRep(ServerPlayer player, ResourceLocation faction) {
        return getRep(player.server, player.getUUID(), faction);
    }

    public static int getRep(MinecraftServer server, UUID player, ResourceLocation faction) {
        if (Factions.RENOWN.equals(faction)) {
            return FealtyWorldData.get(server).existingPlayer(player).map(RepManager::renown).orElse(0);
        }
        PlayerRepData data = FealtyWorldData.get(server).player(player);
        Integer stored = data.rep.get(faction);
        return stored != null ? stored : startingRep(server, data, faction);
    }

    public static RepTier getTier(ServerPlayer player, ResourceLocation faction) {
        return tierOf(getRep(player, faction));
    }

    /** A faction the player has never dealt with starts at a share of their Renown, capped either way. */
    public static int startingRep(MinecraftServer server, PlayerRepData data, ResourceLocation faction) {
        double factor = Factions.renownStartFactor(server, faction);
        int cap = FealtyConfig.RENOWN_START_CAP.get();
        int value = (int) Math.round(data.renown * factor);
        return clamp(Mth.clamp(value, -cap, cap));
    }

    public static boolean hasMet(ServerPlayer player, ResourceLocation faction) {
        return data(player).rep.containsKey(faction);
    }

    /** Fix the starting reputation with a faction the first time the player meets it. */
    public static void meet(ServerPlayer player, ResourceLocation faction) {
        FealtyWorldData world = FealtyWorldData.get(player.server);
        PlayerRepData data = world.player(player.getUUID());
        if (!data.rep.containsKey(faction)) {
            int start = startingRep(player.server, data, faction);
            data.rep.put(faction, start);
            data.name = player.getGameProfile().getName();
            world.setDirty();
            FealtyNetwork.syncStanding(player, faction);
            ModCriteria.REP_TIER.get().trigger(player, faction, start, tierOf(start));
        }
    }

    public static int renown(PlayerRepData data) {
        return (int) Math.round(data.renown);
    }

    public static int renown(ServerPlayer player) {
        return renown(data(player));
    }

    public static RepTier renownTier(ServerPlayer player) {
        return tierOf(renown(player));
    }

    // ---- Writes ----

    /**
     * Change reputation by an amount, applying the design rules: negative reputation can only be raised by
     * sources marked {@code can_raise_negative} (redemption quests), and other mods can cancel or adjust it.
     *
     * @return the change actually applied
     */
    public static int change(ServerPlayer player, ResourceLocation faction, int amount, ResourceLocation reason) {
        return change(player, faction, amount, reason, false);
    }

    /** @param force skip the "negative rep never heals" rule (commands) */
    public static int change(ServerPlayer player, ResourceLocation faction, int amount, ResourceLocation reason, boolean force) {
        if (amount == 0) {
            return 0;
        }
        if (Factions.RENOWN.equals(faction)) {
            return changeRenown(player, amount);
        }
        int old = getRep(player, faction);
        if (!force && amount > 0 && old < 0 && !canRaiseNegative(reason)) {
            return 0;
        }
        RepChangeEvent.Pre pre = NeoForge.EVENT_BUS.post(new RepChangeEvent.Pre(player, faction, reason, old, amount));
        if (pre.isCanceled() || pre.getAmount() == 0) {
            return 0;
        }
        return applyValue(player, faction, old, clamp(old + pre.getAmount()), reason);
    }

    /**
     * Change the reputation of a player who may be offline. No events fire and no rules apply; used for daily
     * effects such as a lord's taxes.
     *
     * @return the change applied
     */
    public static int changeOffline(MinecraftServer server, UUID player, ResourceLocation faction, int amount) {
        FealtyWorldData world = FealtyWorldData.get(server);
        PlayerRepData data = world.player(player);
        int old = getRep(server, player, faction);
        int now = clamp(old + amount);
        data.rep.put(faction, now);
        world.setDirty();
        return now - old;
    }

    /** Set reputation to a value, firing the same events as a change. */
    public static int set(ServerPlayer player, ResourceLocation faction, int value, ResourceLocation reason) {
        if (Factions.RENOWN.equals(faction)) {
            return changeRenown(player, value - renown(player));
        }
        int old = getRep(player, faction);
        int target = clamp(value);
        RepChangeEvent.Pre pre = NeoForge.EVENT_BUS.post(new RepChangeEvent.Pre(player, faction, reason, old, target - old));
        if (pre.isCanceled()) {
            return 0;
        }
        return applyValue(player, faction, old, clamp(old + pre.getAmount()), reason);
    }

    /**
     * Apply a registered rep source with its data pack amount, honouring {@code every} and {@code daily_cap}.
     *
     * @param multiplier scales the configured amount (crime severity)
     * @return the change actually applied
     */
    public static int applySource(ServerPlayer player, ResourceLocation faction, ResourceLocation sourceId, float multiplier) {
        SourceSettings settings = FealtyDataManager.source(sourceId);
        int amount = Math.round(baseAmount(sourceId) * multiplier);
        if (amount == 0) {
            return 0;
        }
        if (settings.every() > 1 || settings.dailyCap() > 0) {
            PlayerRepData data = data(player);
            PlayerRepData.Tally tally = data.tally(faction, sourceId, day(player.server));
            tally.count++;
            FealtyWorldData.get(player.server).setDirty();
            if (tally.count % settings.every() != 0) {
                return 0;
            }
            if (settings.dailyCap() > 0) {
                int room = settings.dailyCap() - tally.applied;
                if (room <= 0) {
                    return 0;
                }
                amount = amount > 0 ? Math.min(amount, room) : amount;
            }
            int applied = change(player, faction, amount, sourceId);
            tally.applied += Math.abs(applied);
            return applied;
        }
        return change(player, faction, amount, sourceId);
    }

    public static int applySource(ServerPlayer player, ResourceLocation faction, ResourceLocation sourceId) {
        return applySource(player, faction, sourceId, 1.0F);
    }

    /** The amount a source applies: the data pack value, else the registered default. */
    public static int baseAmount(ResourceLocation sourceId) {
        SourceSettings settings = FealtyDataManager.source(sourceId);
        if (settings.amount().isPresent()) {
            return settings.amount().get();
        }
        RepSource source = FealtyRegistries.REP_SOURCES.get(sourceId);
        return source != null ? source.defaultAmount() : 0;
    }

    public static boolean isCrime(ResourceLocation sourceId) {
        RepSource source = FealtyRegistries.REP_SOURCES.get(sourceId);
        return source != null && source.crime();
    }

    public static boolean canRaiseNegative(ResourceLocation sourceId) {
        if (FealtyConfig.NEGATIVE_REP_DECAY.get() && RepSources.API.equals(sourceId)) {
            return true;
        }
        SourceSettings settings = FealtyDataManager.source(sourceId);
        if (settings.canRaiseNegative().isPresent()) {
            return settings.canRaiseNegative().get();
        }
        RepSource source = FealtyRegistries.REP_SOURCES.get(sourceId);
        return source != null && source.canRaiseNegative();
    }

    private static int applyValue(ServerPlayer player, ResourceLocation faction, int old, int target, ResourceLocation reason) {
        MinecraftServer server = player.server;
        FealtyWorldData world = FealtyWorldData.get(server);
        PlayerRepData data = world.player(player.getUUID());
        data.name = player.getGameProfile().getName();
        data.rep.put(faction, target);
        world.setDirty();
        int delta = target - old;
        if (delta == 0) {
            return 0;
        }

        double share = Factions.renownShare(server, faction);
        if (share != 0) {
            adjustRenown(player, data, delta * share);
        }

        NeoForge.EVENT_BUS.post(new RepChangeEvent.Post(player, faction, reason, old, target));

        RepTier oldTier = tierOf(old);
        RepTier newTier = tierOf(target);
        if (!oldTier.id().equals(newTier.id())) {
            NeoForge.EVENT_BUS.post(new TierChangedEvent(player, faction, oldTier, newTier));
            announceTier(player, faction, oldTier, newTier);
        }
        ModCriteria.REP_TIER.get().trigger(player, faction, target, newTier);
        FealtyNetwork.syncStanding(player, faction);
        if (FealtyConfig.SHOW_REP_CHANGES.get()) {
            MutableComponent amount = Component.literal((delta > 0 ? "+" : "") + delta)
                    .withStyle(delta > 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
            player.displayClientMessage(Component.translatable("fealty.rep.change", amount, Factions.displayName(server, faction)), true);
        }
        return delta;
    }

    private static int changeRenown(ServerPlayer player, int amount) {
        PlayerRepData data = data(player);
        int before = renown(data);
        adjustRenown(player, data, amount);
        return renown(data) - before;
    }

    private static void adjustRenown(ServerPlayer player, PlayerRepData data, double amount) {
        int before = renown(data);
        data.renown = Mth.clamp(data.renown + amount, min(), max());
        FealtyWorldData.get(player.server).setDirty();
        int after = renown(data);
        if (before != after) {
            RepTier oldTier = tierOf(before);
            RepTier newTier = tierOf(after);
            if (!oldTier.id().equals(newTier.id())) {
                NeoForge.EVENT_BUS.post(new TierChangedEvent(player, Factions.RENOWN, oldTier, newTier));
                announceTier(player, Factions.RENOWN, oldTier, newTier);
            }
            ModCriteria.RENOWN.get().trigger(player, after, newTier);
            FealtyNetwork.syncRenown(player, after);
        }
    }

    private static void announceTier(ServerPlayer player, ResourceLocation faction, RepTier oldTier, RepTier newTier) {
        boolean rising = newTier.rank() > oldTier.rank();
        Component tierName = newTier.displayName().copy().withColor(newTier.color());
        String key = Factions.RENOWN.equals(faction) ? (rising ? "fealty.renown.rise" : "fealty.renown.fall")
                : (rising ? "fealty.tier.rise" : "fealty.tier.fall");
        player.sendSystemMessage(Component.translatable(key, Factions.displayName(player.server, faction), tierName));
        player.level().playSound(null, player.blockPosition(),
                rising ? SoundEvents.PLAYER_LEVELUP : SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6F, rising ? 1.2F : 0.8F);
    }
}
