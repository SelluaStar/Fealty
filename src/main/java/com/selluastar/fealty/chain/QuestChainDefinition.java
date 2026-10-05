package com.selluastar.fealty.chain;

import java.util.ArrayList;
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
 * <p>{@code rare_villager} chains start when a Trusted player asks an elder about rumours. Each run picks
 * {@code steps_count} steps from {@code step_pool} (or uses the fixed {@code steps}), preferring roles the village
 * has villagers for, and one quest variant for each. Each step is given by a named villager of that village;
 * finishing every step awards {@code steps_reward} and a map to a {@code map_structure}, where the Keeper sets one
 * of the {@code trial_quests}. The trial gives {@code trial_reward}, and the Keeper combines both rewards into
 * {@code final_reward}.
 *
 * <p>{@code guild} chains are handed out one step at a time by the thieves guild fence.
 */
public record QuestChainDefinition(Kind kind, ResourceLocation startTier, List<Step> steps, List<Step> stepPool, int stepsCount,
                                   Optional<ItemStack> stepsReward, Optional<TagKey<Structure>> mapStructure,
                                   Optional<ResourceLocation> trialQuest, List<ResourceLocation> trialQuests,
                                   Optional<ItemStack> trialReward, Optional<ItemStack> finalReward, boolean repeatable) {
    public static final Codec<QuestChainDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(QuestChainDefinition::kind),
            ResourceLocation.CODEC.optionalFieldOf("start_tier", ResourceLocation.fromNamespaceAndPath("fealty", "trusted")).forGetter(QuestChainDefinition::startTier),
            Step.CODEC.listOf().optionalFieldOf("steps", List.of()).forGetter(QuestChainDefinition::steps),
            Step.CODEC.listOf().optionalFieldOf("step_pool", List.of()).forGetter(QuestChainDefinition::stepPool),
            Codec.intRange(1, 16).optionalFieldOf("steps_count", 3).forGetter(QuestChainDefinition::stepsCount),
            ItemStack.CODEC.optionalFieldOf("steps_reward").forGetter(QuestChainDefinition::stepsReward),
            TagKey.hashedCodec(Registries.STRUCTURE).optionalFieldOf("map_structure").forGetter(QuestChainDefinition::mapStructure),
            ResourceLocation.CODEC.optionalFieldOf("trial_quest").forGetter(QuestChainDefinition::trialQuest),
            ResourceLocation.CODEC.listOf().optionalFieldOf("trial_quests", List.of()).forGetter(QuestChainDefinition::trialQuests),
            ItemStack.CODEC.optionalFieldOf("trial_reward").forGetter(QuestChainDefinition::trialReward),
            ItemStack.CODEC.optionalFieldOf("final_reward").forGetter(QuestChainDefinition::finalReward),
            Codec.BOOL.optionalFieldOf("repeatable", true).forGetter(QuestChainDefinition::repeatable)
    ).apply(i, QuestChainDefinition::new));

    /** Whether runs pick their steps from a pool rather than using the fixed steps. */
    public boolean pooled() {
        return !stepPool.isEmpty();
    }

    /** The step for a role, from the pool or the fixed steps. */
    public Optional<Step> step(String role) {
        for (Step step : stepPool) {
            if (step.role().equals(role)) {
                return Optional.of(step);
            }
        }
        for (Step step : steps) {
            if (step.role().equals(role)) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }

    /** Every trial the Keeper may set. */
    public List<ResourceLocation> trials() {
        List<ResourceLocation> trials = new ArrayList<>(trialQuests);
        trialQuest.filter(id -> !trials.contains(id)).ifPresent(trials::add);
        return trials;
    }

    /**
     * One step of a chain.
     *
     * @param role        role key, used for the villager's title ({@code fealty.chain.role.<role>}) and greeting
     *                    ({@code fealty.chain.villager.greet.<role>})
     * @param professions villager professions that can take this role ({@code minecraft:nitwit} and
     *                    {@code minecraft:none} included); empty means anyone
     * @param quest       the step's quest, or
     * @param variants    several quests, one of which is picked for each run
     */
    public record Step(String role, List<ResourceLocation> professions, Optional<Component> title, Optional<ResourceLocation> quest,
                       List<ResourceLocation> variants, List<ItemStack> rewards) {
        public static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("role", "helper").forGetter(Step::role),
                ResourceLocation.CODEC.listOf().optionalFieldOf("professions", List.of()).forGetter(Step::professions),
                ComponentSerialization.CODEC.optionalFieldOf("title").forGetter(Step::title),
                ResourceLocation.CODEC.optionalFieldOf("quest").forGetter(Step::quest),
                ResourceLocation.CODEC.listOf().optionalFieldOf("variants", List.of()).forGetter(Step::variants),
                ItemStack.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Step::rewards)
        ).apply(i, Step::new));

        /** The quests this step can set: its variants, then its quest. */
        public List<ResourceLocation> quests() {
            List<ResourceLocation> quests = new ArrayList<>(variants);
            quest.filter(id -> !quests.contains(id)).ifPresent(quests::add);
            return quests;
        }

        public Optional<ResourceLocation> firstQuest() {
            List<ResourceLocation> quests = quests();
            return quests.isEmpty() ? Optional.empty() : Optional.of(quests.getFirst());
        }
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
