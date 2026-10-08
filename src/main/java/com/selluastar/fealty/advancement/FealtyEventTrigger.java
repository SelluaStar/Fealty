package com.selluastar.fealty.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code fealty:event}: a story moment happened. See {@link FealtyEvents} for the ids.
 *
 * <pre>{@code
 * "criteria": { "writ": { "trigger": "fealty:event", "conditions": { "event": "fealty:writ_forged" } } }
 * }</pre>
 */
public class FealtyEventTrigger extends SimpleCriterionTrigger<FealtyEventTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ResourceLocation event) {
        trigger(player, i -> i.event.equals(event));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, ResourceLocation event)
            implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ResourceLocation.CODEC.fieldOf("event").forGetter(TriggerInstance::event)
        ).apply(i, TriggerInstance::new));
    }
}
