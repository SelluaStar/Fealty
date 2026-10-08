package com.selluastar.fealty.story;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.dialogue.DialogueCondition;
import com.selluastar.fealty.api.dialogue.DialogueEffect;
import com.selluastar.fealty.story.script.Texts;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * A rumour from {@code data/<ns>/fealty/rumours/<id>.json}.
 *
 * <pre>{@code
 * {
 *   "chain": "example:tale", "stage": 1,
 *   "min_tier": "fealty:trusted", "weight": 5, "professions": [], "told_by": "villagers",
 *   "lines": ["example.rumour.stranger"],
 *   "requires": {"time_of_day": "night"},
 *   "grants": [{"give_item": "minecraft:paper"}]
 * }
 * }</pre>
 *
 * A rumour of a {@code chain} tells of one of its stages: stage 1 is told while the chain can start for the player in
 * the teller's village, and hearing it starts the chain there; a later stage is told while a running chain is one
 * stage short of it, and moves it on. A rumour of no chain is told once to each player. {@code lines} are translation
 * keys (or text components); the teller says one of them.
 */
public record RumourDefinition(Optional<ResourceLocation> chain, int stage, ResourceLocation minTier, int weight, List<ResourceLocation> professions,
                               List<Component> lines, Optional<DialogueCondition> requires, List<DialogueEffect> grants, Teller toldBy) {
    public static final Codec<RumourDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("chain").forGetter(RumourDefinition::chain),
            Codec.intRange(1, 4).optionalFieldOf("stage", 1).forGetter(RumourDefinition::stage),
            ResourceLocation.CODEC.optionalFieldOf("min_tier", ResourceLocation.fromNamespaceAndPath("fealty", "neutral")).forGetter(RumourDefinition::minTier),
            Codec.intRange(1, 10000).optionalFieldOf("weight", 1).forGetter(RumourDefinition::weight),
            ResourceLocation.CODEC.listOf().optionalFieldOf("professions", List.of()).forGetter(RumourDefinition::professions),
            Texts.CODEC.listOf().optionalFieldOf("lines", List.of()).forGetter(RumourDefinition::lines),
            DialogueCondition.CODEC.optionalFieldOf("requires").forGetter(RumourDefinition::requires),
            DialogueEffect.CODEC.listOf().optionalFieldOf("grants", List.of()).forGetter(RumourDefinition::grants),
            Teller.CODEC.optionalFieldOf("told_by", Teller.ANY).forGetter(RumourDefinition::toldBy)
    ).apply(i, RumourDefinition::new));

    /** Who tells a rumour: villagers (in conversation), village elders (asked about rumours), or both. */
    public enum Teller implements StringRepresentable {
        ANY("any"),
        VILLAGERS("villagers"),
        ELDERS("elders");

        public static final Codec<Teller> CODEC = StringRepresentable.fromEnum(Teller::values);
        private final String name;

        Teller(String name) {
            this.name = name;
        }

        public boolean tells(boolean elder) {
            return this == ANY || (this == ELDERS) == elder;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
