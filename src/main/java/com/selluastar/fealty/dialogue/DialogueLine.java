package com.selluastar.fealty.dialogue;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * One thing an NPC can say, with the conditions under which they say it. Empty condition lists match anything.
 *
 * @param tiers       the listener's tier with the speaker's faction
 * @param professions the speaker's villager profession
 * @param entities    the speaker's entity type
 */
public record DialogueLine(Component text, int weight, List<ResourceLocation> tiers, List<ResourceLocation> professions,
                           List<ResourceLocation> entities, Optional<Boolean> lord, Optional<Boolean> broken, Optional<Boolean> night) {
    public static final Codec<DialogueLine> CODEC = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("text").forGetter(DialogueLine::text),
            Codec.intRange(1, 1000).optionalFieldOf("weight", 1).forGetter(DialogueLine::weight),
            ResourceLocation.CODEC.listOf().optionalFieldOf("tiers", List.of()).forGetter(DialogueLine::tiers),
            ResourceLocation.CODEC.listOf().optionalFieldOf("professions", List.of()).forGetter(DialogueLine::professions),
            ResourceLocation.CODEC.listOf().optionalFieldOf("entities", List.of()).forGetter(DialogueLine::entities),
            Codec.BOOL.optionalFieldOf("lord").forGetter(DialogueLine::lord),
            Codec.BOOL.optionalFieldOf("broken").forGetter(DialogueLine::broken),
            Codec.BOOL.optionalFieldOf("night").forGetter(DialogueLine::night)
    ).apply(i, DialogueLine::new));

    public boolean matches(SpeakerContext context) {
        if (!tiers.isEmpty() && (context.tier() == null || !tiers.contains(context.tier()))) {
            return false;
        }
        if (!professions.isEmpty() && (context.profession() == null || !professions.contains(context.profession()))) {
            return false;
        }
        if (!entities.isEmpty() && !entities.contains(context.entityType())) {
            return false;
        }
        if (lord.isPresent() && lord.get() != context.lord()) {
            return false;
        }
        if (broken.isPresent() && broken.get() != context.broken()) {
            return false;
        }
        return night.isEmpty() || night.get() == context.night();
    }

    /** A data pack file of lines for one context, e.g. {@code greet} or {@code crime_theft}. */
    public record File(String context, List<DialogueLine> lines) {
        public static final Codec<File> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("context").forGetter(File::context),
                DialogueLine.CODEC.listOf().fieldOf("lines").forGetter(File::lines)
        ).apply(i, File::new));
    }
}
