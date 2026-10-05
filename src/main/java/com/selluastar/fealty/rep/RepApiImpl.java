package com.selluastar.fealty.rep;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.api.RepApi;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.outlaw.HeatManager;
import com.selluastar.fealty.trade.PricingService;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/** The live {@link RepApi}, registered with {@code FealtyApi} at startup. */
public final class RepApiImpl implements RepApi {
    @Override
    public int getRep(ServerPlayer player, ResourceLocation faction) {
        return RepManager.getRep(player, faction);
    }

    @Override
    public RepTier getTier(ServerPlayer player, ResourceLocation faction) {
        return RepManager.getTier(player, faction);
    }

    @Override
    public void addRep(ServerPlayer player, ResourceLocation faction, int amount, ResourceLocation reason) {
        RepManager.change(player, faction, amount, reason);
    }

    @Override
    public float priceMultiplier(ServerPlayer player, Villager villager) {
        return PricingService.multiplierFor(player, villager, false);
    }

    @Override
    public boolean isWanted(ServerPlayer player, ResourceLocation faction) {
        return HeatManager.wantedLevel(player, faction) > 0;
    }

    @Override
    public int getRep(MinecraftServer server, UUID player, ResourceLocation faction) {
        return RepManager.getRep(server, player, faction);
    }

    @Override
    public void setRep(ServerPlayer player, ResourceLocation faction, int value, ResourceLocation reason) {
        RepManager.set(player, faction, value, reason);
    }

    @Override
    public int applySource(ServerPlayer player, ResourceLocation faction, ResourceLocation source) {
        return RepManager.applySource(player, faction, source);
    }

    @Override
    public int getRenown(ServerPlayer player) {
        return RepManager.renown(player);
    }

    @Override
    public RepTier getRenownTier(ServerPlayer player) {
        return RepManager.renownTier(player);
    }

    @Override
    public int getHeat(ServerPlayer player, ResourceLocation faction) {
        return HeatManager.heat(player, faction);
    }

    @Override
    public Optional<ResourceLocation> getFactionOf(Entity entity) {
        return FactionResolver.factionOf(entity);
    }

    @Override
    public Optional<ResourceLocation> getFactionAt(ServerLevel level, BlockPos pos) {
        return VillageResolver.villageAt(level, pos).map(VillageRecord::id);
    }

    @Override
    public Optional<UUID> getLord(MinecraftServer server, ResourceLocation village) {
        return FealtyWorldData.get(server).village(village).map(r -> r.lord().uuid());
    }

    @Override
    public List<RepTier> getTiers() {
        return TierManager.tiers();
    }

    @Override
    public Optional<RepTier> getTier(ResourceLocation tierId) {
        return TierManager.byId(tierId);
    }
}
