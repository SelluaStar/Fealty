package com.selluastar.fealty.rep;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.Campaign;
import com.selluastar.fealty.api.RepApi;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.api.Stronghold;
import com.selluastar.fealty.api.VillageInfo;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.lordship.LordshipManager;
import com.selluastar.fealty.outlaw.HeatManager;
import com.selluastar.fealty.story.PlayerFlags;
import com.selluastar.fealty.trade.PricingService;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;
import com.selluastar.fealty.war.Campaigns;
import com.selluastar.fealty.war.Captives;
import com.selluastar.fealty.war.Strongholds;
import com.selluastar.fealty.war.WarDefenders;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

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

    @Override
    public List<Stronghold> getStrongholds(MinecraftServer server) {
        long day = RepManager.day(server);
        return Strongholds.get(server).all().stream().map(e -> e.view(day)).toList();
    }

    @Override
    public Optional<Stronghold> getNearestStronghold(ServerLevel level, BlockPos pos, double radius) {
        long day = RepManager.day(level.getServer());
        return Strongholds.get(level.getServer()).near(level.dimension(), pos, radius).stream().findFirst().map(e -> e.view(day));
    }

    @Override
    public Optional<Campaign> getCampaign(MinecraftServer server, UUID lord) {
        return Campaigns.of(server, lord);
    }

    @Override
    public boolean isAtPeace(MinecraftServer server, ResourceLocation village) {
        long day = RepManager.day(server);
        return FealtyWorldData.get(server).village(village).map(v -> v.sites().atPeace(day)).orElse(false);
    }

    @Override
    public Optional<Component> declareRaid(ServerPlayer lord, ResourceLocation village, UUID stronghold) {
        Optional<VillageRecord> record = FealtyWorldData.get(lord.server).village(village);
        Optional<Strongholds.Entry> target = Strongholds.get(lord.server).get(stronghold);
        if (record.isEmpty() || target.isEmpty()) {
            return Optional.of(Component.translatable("fealty.war.target_gone"));
        }
        return Campaigns.declare(lord, record.get(), target.get(), LordshipManager.inVillage(lord, record.get()));
    }

    @Override
    public boolean isCaptive(Entity entity) {
        return Captives.isCaptive(entity);
    }

    @Override
    public boolean isWarbandMember(Entity entity) {
        return Campaigns.isWarbandMember(entity);
    }

    @Override
    public boolean isStrongholdDefender(Entity entity) {
        return WarDefenders.isDefender(entity);
    }

    // ---- Stories ----

    @Override
    public int getFlag(ServerPlayer player, ResourceLocation flag) {
        return PlayerFlags.get(player, flag);
    }

    @Override
    public void setFlag(ServerPlayer player, ResourceLocation flag, int value) {
        PlayerFlags.set(player, flag, value);
    }

    @Override
    public void clearFlag(ServerPlayer player, ResourceLocation flag) {
        PlayerFlags.clear(player, flag);
    }

    @Override
    public boolean assignRole(Villager villager, ResourceLocation chain, String role, @Nullable Component title) {
        return ChainManager.assignRole(villager, chain, role, title);
    }

    @Override
    public void clearRole(Villager villager) {
        if (ChainManager.hasRole(villager)) {
            ChainManager.clearRole(villager);
        }
    }

    @Override
    public boolean hasRole(Villager villager, ResourceLocation chain, String role) {
        return ChainManager.holdsRole(villager, chain, role);
    }

    @Override
    public Optional<Villager> findVillager(MinecraftServer server, ResourceLocation village, Predicate<Villager> predicate) {
        Optional<VillageRecord> record = FealtyWorldData.get(server).village(village);
        ServerLevel level = record.map(r -> server.getLevel(r.dimension())).orElse(null);
        if (record.isEmpty() || level == null) {
            return Optional.empty();
        }
        BlockPos center = record.get().center();
        return level.getEntitiesOfClass(Villager.class, AABB.of(record.get().bounds()).inflate(16),
                        v -> v.isAlive() && village.equals(FactionResolver.factionOf(v).orElse(null)) && predicate.test(v))
                .stream().min(Comparator.comparingDouble(v -> v.blockPosition().distSqr(center)));
    }

    @Override
    public Optional<VillageInfo> getVillage(MinecraftServer server, ResourceLocation village) {
        return FealtyWorldData.get(server).village(village).map(RepApiImpl::info);
    }

    @Override
    public Optional<VillageInfo> findVillage(MinecraftServer server, GlobalPos origin, int minDistance, int maxDistance,
                                             Predicate<VillageInfo> predicate) {
        double min = (double) minDistance * minDistance;
        double max = (double) maxDistance * maxDistance;
        Optional<VillageInfo> known = FealtyWorldData.get(server).villages().stream()
                .filter(v -> v.dimension().equals(origin.dimension()))
                .filter(v -> {
                    double d = v.center().distSqr(origin.pos());
                    return d >= min && d <= max;
                })
                .sorted(Comparator.comparingDouble(v -> v.center().distSqr(origin.pos())))
                .map(RepApiImpl::info).filter(predicate).findFirst();
        if (known.isPresent()) {
            return known;
        }
        // One the world has but Fealty has not seen yet: the nearest village structure, if it is far enough.
        ServerLevel level = server.getLevel(origin.dimension());
        BlockPos found = level == null ? null : level.findNearestMapStructure(StructureTags.VILLAGE, origin.pos(), Math.max(1, maxDistance / 16), false);
        if (found == null) {
            return Optional.empty();
        }
        double d = found.distSqr(origin.pos());
        if (d < min || d > max) {
            return Optional.empty();
        }
        return VillageResolver.villageAt(level, found).map(RepApiImpl::info).filter(predicate);
    }

    private static VillageInfo info(VillageRecord village) {
        return new VillageInfo(village.id(), village.name(), village.dimension(), village.center(), village.bounds(), village.hasElder(),
                Optional.ofNullable(village.lord().uuid()), village.isBroken());
    }

    @Override
    public boolean startChain(ServerPlayer player, ResourceLocation chain, ResourceLocation village) {
        return FealtyWorldData.get(player.server).village(village).map(v -> ChainManager.start(player, chain, v)).orElse(false);
    }

    @Override
    public int getChainStage(ServerPlayer player, ResourceLocation chain) {
        return ChainManager.stage(player, chain);
    }
}
