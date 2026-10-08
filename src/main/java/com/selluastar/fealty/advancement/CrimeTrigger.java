package com.selluastar.fealty.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** {@code fealty:crime}: the player committed a crime, optionally a specific one, seen or unseen. */
public class CrimeTrigger extends SimpleCriterionTrigger<CrimeTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ResourceLocation crime, boolean witnessed) {
        trigger(player, i -> i.crime.map(crime::equals).orElse(true) && i.witnessed.map(w -> w == witnessed).orElse(true));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> crime,
                                  Optional<Boolean> witnessed) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ResourceLocation.CODEC.optionalFieldOf("crime").forGetter(TriggerInstance::crime),
                Codec.BOOL.optionalFieldOf("witnessed").forGetter(TriggerInstance::witnessed)
        ).apply(i, TriggerInstance::new));
    }
}
