package com.selluastar.fealty.loot;

import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.advancement.RepTierTrigger;
import com.selluastar.fealty.registry.ModLootConditions;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.minecraft.world.phys.Vec3;

/**
 * {@code fealty:rep_tier} loot condition, usable in loot tables, predicates and advancement player conditions.
 *
 * <pre>{@code
 * { "condition": "fealty:rep_tier", "entity": "this", "faction": "here", "min_tier": "fealty:trusted" }
 * }</pre>
 * {@code faction} is a faction id, {@code "fealty:renown"} for Renown, or {@code "here"} for the village at
 * the loot origin.
 */
public record RepTierCondition(LootContext.EntityTarget entity, ResourceLocation faction, Optional<ResourceLocation> minTier,
                               Optional<ResourceLocation> maxTier, MinMaxBounds.Ints rep) implements LootItemCondition {
    public static final ResourceLocation HERE = ResourceLocation.withDefaultNamespace("here");

    public static final MapCodec<RepTierCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LootContext.EntityTarget.CODEC.optionalFieldOf("entity", LootContext.EntityTarget.THIS).forGetter(RepTierCondition::entity),
            ResourceLocation.CODEC.optionalFieldOf("faction", HERE).forGetter(RepTierCondition::faction),
            ResourceLocation.CODEC.optionalFieldOf("min_tier").forGetter(RepTierCondition::minTier),
            ResourceLocation.CODEC.optionalFieldOf("max_tier").forGetter(RepTierCondition::maxTier),
            MinMaxBounds.Ints.CODEC.optionalFieldOf("rep", MinMaxBounds.Ints.ANY).forGetter(RepTierCondition::rep)
    ).apply(i, RepTierCondition::new));

    @Override
    public LootItemConditionType getType() {
        return ModLootConditions.REP_TIER.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return HERE.equals(faction) ? Set.of(entity.getParam(), LootContextParams.ORIGIN) : Set.of(entity.getParam());
    }

    @Override
    public boolean test(LootContext context) {
        Entity e = context.getParamOrNull(entity.getParam());
        if (!(e instanceof ServerPlayer player)) {
            return false;
        }
        ResourceLocation target = faction;
        if (HERE.equals(faction)) {
            Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
            if (origin == null) {
                return false;
            }
            Optional<VillageRecord> village = VillageResolver.villageAt(context.getLevel(), BlockPos.containing(origin));
            if (village.isEmpty()) {
                return false;
            }
            target = village.get().id();
        }
        int value = Factions.RENOWN.equals(target) ? RepManager.renown(player) : RepManager.getRep(player, target);
        return rep.matches(value) && RepTierTrigger.TierRange.matches(RepManager.tierOf(value), minTier, maxTier);
    }
}
