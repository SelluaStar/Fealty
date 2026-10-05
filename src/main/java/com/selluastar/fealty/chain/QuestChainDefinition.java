package com.selluastar.fealty.chain;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * A quest chain from {@code data/<ns>/fealty/quest_chains/<id>.json}.
 *
 * <p>{@code rare_villager} chains start when a Trusted player asks an elder about rumours. Each step is given by a
 * named villager of that village; finishing every step awards {@code steps_reward} and a map to a
 * {@code map_structure}, where the Keeper sets {@code trial_quest}. The trial gives {@code trial_reward}, and
 * the Keeper combines both rewards into {@code final_reward}.
 *
 * <p>{@code guild} chains are handed out one step at a time by the thieves guild fence.
 */
public record QuestChainDefinition(Kind kind, ResourceLocation startTier, List<Step> steps, Optional<ItemStack> stepsReward,
                                   Optional<TagKey<Structure>> mapStructure, Optional<ResourceLocation> trialQuest,
                                   Optional<ItemStack> trialReward, Optional<ItemStack> finalReward, boolean repeatable) {
    public static final Codec<QuestChainDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(QuestChainDefinition::kind),
            ResourceLocation.CODEC.optionalFieldOf("start_tier", ResourceLocation.fromNamespaceAndPath("fealty", "trusted")).forGetter(QuestChainDefinition::startTier),
            Step.CODEC.listOf().fieldOf("steps").forGetter(QuestChainDefinition::steps),
            ItemStack.CODEC.optionalFieldOf("steps_reward").forGetter(QuestChainDefinition::stepsReward),
            TagKey.hashedCodec(Registries.STRUCTURE).optionalFieldOf("map_structure").forGetter(QuestChainDefinition::mapStructure),
            ResourceLocation.CODEC.optionalFieldOf("trial_quest").forGetter(QuestChainDefinition::trialQuest),
            ItemStack.CODEC.optionalFieldOf("trial_reward").forGetter(QuestChainDefinition::trialReward),
            ItemStack.CODEC.optionalFieldOf("final_reward").forGetter(QuestChainDefinition::finalReward),
            Codec.BOOL.optionalFieldOf("repeatable", true).forGetter(QuestChainDefinition::repeatable)
    ).apply(i, QuestChainDefinition::new));

    /**
     * One step of a chain.
     *
     * @param role        role key, used for the villager's title ({@code fealty.chain.role.<role>})
     * @param professions villager professions preferred for this role
     */
    public record Step(String role, List<ResourceLocation> professions, Optional<Component> title, ResourceLocation quest,
                       List<ItemStack> rewards) {
        public static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("role", "helper").forGetter(Step::role),
                ResourceLocation.CODEC.listOf().optionalFieldOf("professions", List.of()).forGetter(Step::professions),
                ComponentSerialization.CODEC.optionalFieldOf("title").forGetter(Step::title),
                ResourceLocation.CODEC.fieldOf("quest").forGetter(Step::quest),
                ItemStack.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Step::rewards)
        ).apply(i, Step::new));
    }

    public enum Kind implements StringRepresentable {
        RARE_VILLAGER("rare_villager"),
        GUILD("guild");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
