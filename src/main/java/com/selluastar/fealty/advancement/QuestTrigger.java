package com.selluastar.fealty.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;

/** {@code fealty:rep_quest}: a trusting villager quest started, completed or failed. */
public class QuestTrigger extends SimpleCriterionTrigger<QuestTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, ResourceLocation quest, ResourceLocation type, Status status) {
        trigger(player, i -> i.quest.map(quest::equals).orElse(true)
                && i.type.map(type::equals).orElse(true)
                && i.status == status);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ResourceLocation> quest,
                                  Optional<ResourceLocation> type, Status status) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ResourceLocation.CODEC.optionalFieldOf("quest").forGetter(TriggerInstance::quest),
                ResourceLocation.CODEC.optionalFieldOf("type").forGetter(TriggerInstance::type),
                Status.CODEC.optionalFieldOf("status", Status.COMPLETED).forGetter(TriggerInstance::status)
        ).apply(i, TriggerInstance::new));
    }

    public enum Status implements StringRepresentable {
        STARTED("started"),
        COMPLETED("completed"),
        FAILED("failed");

        public static final Codec<Status> CODEC = StringRepresentable.fromEnum(Status::values);
        private final String name;

        Status(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
