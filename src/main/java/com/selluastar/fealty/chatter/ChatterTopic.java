package com.selluastar.fealty.chatter;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

/**
 * One thing two villagers can chat about, from {@code data/<ns>/fealty/chatter/<name>.json}: a few lines they say in
 * turn (the first by the villager who walks up to the other), and what either tells a player who asks what they were
 * talking about.
 *
 * @param weight      how likely it is against the other topics
 * @param lines       two to six lines, spoken alternately
 * @param tell        what a villager says when asked what the chat was about
 * @param professions if not empty, at least one of the two must have one of these professions
 * @param night       only at night (true) or only by day (false); both when absent
 */
public record ChatterTopic(int weight, List<Component> lines, Component tell, List<ResourceLocation> professions, Optional<Boolean> night) {
    public static final Codec<ChatterTopic> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1, 1000).optionalFieldOf("weight", 1).forGetter(ChatterTopic::weight),
            ComponentSerialization.CODEC.listOf().validate(ChatterTopic::checkLines).fieldOf("lines").forGetter(ChatterTopic::lines),
            ComponentSerialization.CODEC.fieldOf("tell").forGetter(ChatterTopic::tell),
            ResourceLocation.CODEC.listOf().optionalFieldOf("professions", List.of()).forGetter(ChatterTopic::professions),
            Codec.BOOL.optionalFieldOf("night").forGetter(ChatterTopic::night)
    ).apply(i, ChatterTopic::new));

    private static DataResult<List<Component>> checkLines(List<Component> lines) {
        return lines.size() >= 2 && lines.size() <= 6 ? DataResult.success(lines)
                : DataResult.error(() -> "a chat has two to six lines, not " + lines.size());
    }

    public boolean matches(Villager first, Villager second, boolean isNight) {
        if (night.isPresent() && night.get() != isNight) {
            return false;
        }
        if (professions.isEmpty()) {
            return true;
        }
        return professions.contains(profession(first)) || professions.contains(profession(second));
    }

    private static ResourceLocation profession(Villager villager) {
        return BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
    }
}
