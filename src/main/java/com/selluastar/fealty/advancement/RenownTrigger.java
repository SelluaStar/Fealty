package com.selluastar.fealty.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.RepTier;

import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** {@code fealty:renown}: the player's global Renown is in a range or tier range. */
public class RenownTrigger extends SimpleCriterionTrigger<RenownTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int renown, RepTier tier) {
        trigger(player, instance -> instance.renown.matches(renown) && RepTierTrigger.TierRange.matches(tier, instance.minTier, instance.maxTier));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, MinMaxBounds.Ints renown,
                                  Optional<ResourceLocation> minTier, Optional<ResourceLocation> maxTier)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                MinMaxBounds.Ints.CODEC.optionalFieldOf("renown", MinMaxBounds.Ints.ANY).forGetter(TriggerInstance::renown),
                ResourceLocation.CODEC.optionalFieldOf("min_tier").forGetter(TriggerInstance::minTier),
                ResourceLocation.CODEC.optionalFieldOf("max_tier").forGetter(TriggerInstance::maxTier)
        ).apply(i, TriggerInstance::new));
    }
}
