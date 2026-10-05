package com.selluastar.fealty.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.rep.Factions;

import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code fealty:rep_tier}: the player's reputation with a faction is in a tier range. Fires on every change.
 *
 * <pre>{@code
 * "criteria": { "trusted": { "trigger": "fealty:rep_tier",
 *     "conditions": { "villages_only": true, "min_tier": "fealty:trusted" } } }
 * }</pre>
 */
public class RepTierTrigger extends SimpleCriterionTrigger<RepTierTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ResourceLocation faction, int rep, RepTier tier) {
        trigger(player, instance -> instance.matches(faction, rep, tier));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> faction,
                                  boolean villagesOnly, Optional<ResourceLocation> minTier, Optional<ResourceLocation> maxTier,
                                  MinMaxBounds.Ints rep) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(TriggerInstance::faction),
                Codec.BOOL.optionalFieldOf("villages_only", false).forGetter(TriggerInstance::villagesOnly),
                ResourceLocation.CODEC.optionalFieldOf("min_tier").forGetter(TriggerInstance::minTier),
                ResourceLocation.CODEC.optionalFieldOf("max_tier").forGetter(TriggerInstance::maxTier),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("rep", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::rep)
        ).apply(i, TriggerInstance::new));

        boolean matches(ResourceLocation faction, int rep, RepTier tier) {
            if (this.faction.isPresent() && !this.faction.get().equals(faction)) {
                return false;
            }
            if (villagesOnly && !Factions.isVillage(faction)) {
                return false;
            }
            return TierRange.matches(tier, minTier, maxTier) && this.rep.matches(rep);
        }
    }

    /** Shared tier range check by rank. */
    public static final class TierRange {
        private TierRange() {
        }

        public static boolean matches(RepTier tier, Optional<ResourceLocation> min, Optional<ResourceLocation> max) {
            if (min.isPresent()) {
                Optional<RepTier> minTier = TierManager.byId(min.get());
                if (minTier.isEmpty() || tier.rank() < minTier.get().rank()) {
                    return false;
                }
            }
            if (max.isPresent()) {
                Optional<RepTier> maxTier = TierManager.byId(max.get());
                return maxTier.isPresent() && tier.rank() <= maxTier.get().rank();
            }
            return true;
        }
    }
}
