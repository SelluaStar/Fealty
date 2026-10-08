package com.selluastar.fealty.chain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.dialogue.DialogueCondition;
import com.selluastar.fealty.story.script.Texts;

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
 * <p>{@code kind} names a {@code fealty:chain_kind} (a namespace-less name is Fealty's). {@code rare_villager} chains
 * start when a Trusted player asks an elder about rumours. Each run picks {@code steps_count} steps from
 * {@code step_pool} (or uses the fixed {@code steps}), preferring roles the village has villagers for, and one quest
 * variant for each. Each step is given by a named villager of that village; finishing every step awards
 * {@code steps_reward} and a map to a {@code map_structure}, where the Keeper sets one of the {@code trial_quests}. The
 * trial gives {@code trial_reward}, and the Keeper combines both rewards into {@code final_reward}.
 *
 * <p>{@code guild} chains are handed out one step at a time by the thieves guild fence. {@code story} chains start
 * from a rumour, a conversation or the API, and end with {@code final_reward} once their last step is handed in; other
 * mods add kinds of their own.
 *
 * <p>{@code "giver": "single"} has one villager of the village, holding the role {@code giver_role} (shown with
 * {@code giver_title}), offer every step in order instead of a named villager for each. A step's {@code unlock}
 * condition keeps it closed (showing {@code unlock_hint}) until it passes.
 */
public record QuestChainDefinition(ResourceLocation kind, ResourceLocation startTier, List<Step> steps, List<Step> stepPool, int stepsCount,
                                   Optional<ItemStack> stepsReward, Optional<TagKey<Structure>> mapStructure,
                                   Optional<ResourceLocation> trialQuest, List<ResourceLocation> trialQuests,
                                   Optional<ItemStack> trialReward, Optional<ItemStack> finalReward, boolean repeatable,
                                   GiverMode giver, String giverRole, Optional<Component> giverTitle) {
    /** A kind id; without a namespace it is Fealty's ({@code "rare_villager"} is {@code fealty:rare_villager}). */
    public static final Codec<ResourceLocation> KIND_CODEC = Codec.STRING.comapFlatMap(name -> {
        ResourceLocation id = name.indexOf(':') >= 0 ? ResourceLocation.tryParse(name) : ResourceLocation.tryBuild("fealty", name);
        return id != null ? DataResult.success(id) : DataResult.error(() -> "Not a chain kind id: " + name);
    }, ResourceLocation::toString);

    public static final Codec<QuestChainDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            KIND_CODEC.fieldOf("kind").forGetter(QuestChainDefinition::kind),
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
            Codec.BOOL.optionalFieldOf("repeatable", true).forGetter(QuestChainDefinition::repeatable),
            GiverMode.CODEC.optionalFieldOf("giver", GiverMode.PER_STEP).forGetter(QuestChainDefinition::giver),
            Codec.STRING.optionalFieldOf("giver_role", "giver").forGetter(QuestChainDefinition::giverRole),
            Texts.CODEC.optionalFieldOf("giver_title").forGetter(QuestChainDefinition::giverTitle)
    ).apply(i, QuestChainDefinition::new));

    public boolean isRare() {
        return kind.equals(ChainKinds.RARE_VILLAGER);
    }

    public boolean isGuild() {
        return kind.equals(ChainKinds.GUILD);
    }

    /** Whether one villager gives every step. */
    public boolean single() {
        return giver == GiverMode.SINGLE;
    }

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
     * @param unlock      a condition the step waits for before it is offered
     * @param unlockHint  what the giver says while it waits
     * @param text        what the giver says when offering the step
     */
    public record Step(String role, List<ResourceLocation> professions, Optional<Component> title, Optional<ResourceLocation> quest,
                       List<ResourceLocation> variants, List<ItemStack> rewards, Optional<DialogueCondition> unlock,
                       Optional<Component> unlockHint, Optional<Component> text) {
        public static final Codec<Step> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("role", "helper").forGetter(Step::role),
                ResourceLocation.CODEC.listOf().optionalFieldOf("professions", List.of()).forGetter(Step::professions),
                ComponentSerialization.CODEC.optionalFieldOf("title").forGetter(Step::title),
                ResourceLocation.CODEC.optionalFieldOf("quest").forGetter(Step::quest),
                ResourceLocation.CODEC.listOf().optionalFieldOf("variants", List.of()).forGetter(Step::variants),
                ItemStack.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Step::rewards),
                DialogueCondition.CODEC.optionalFieldOf("unlock").forGetter(Step::unlock),
                Texts.CODEC.optionalFieldOf("unlock_hint").forGetter(Step::unlockHint),
                Texts.CODEC.optionalFieldOf("text").forGetter(Step::text)
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

    /** Who gives a chain's steps: a named villager for each ({@code per_step}), or one villager for all ({@code single}). */
    public enum GiverMode implements StringRepresentable {
        PER_STEP("per_step"),
        SINGLE("single");

        public static final Codec<GiverMode> CODEC = StringRepresentable.fromEnum(GiverMode::values);
        private final String name;

        GiverMode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
