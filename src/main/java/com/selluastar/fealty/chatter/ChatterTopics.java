package com.selluastar.fealty.chatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;

/** The topics from {@code fealty/chatter/}: small talk villagers have with each other. */
public final class ChatterTopics {
    private static Map<ResourceLocation, ChatterTopic> topics = Map.of();

    private ChatterTopics() {
    }

    public static void apply(Map<ResourceLocation, ChatterTopic> loaded) {
        topics = Map.copyOf(loaded);
    }

    public static Map<ResourceLocation, ChatterTopic> all() {
        return topics;
    }

    /** A weighted random topic that suits these two villagers at this time of day. */
    public static Optional<Topic> pick(RandomSource random, Villager first, Villager second, boolean night) {
        List<Map.Entry<ResourceLocation, ChatterTopic>> candidates = new ArrayList<>();
        int total = 0;
        for (Map.Entry<ResourceLocation, ChatterTopic> entry : topics.entrySet()) {
            if (entry.getValue().matches(first, second, night)) {
                candidates.add(entry);
                total += entry.getValue().weight();
            }
        }
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (Map.Entry<ResourceLocation, ChatterTopic> entry : candidates) {
            roll -= entry.getValue().weight();
            if (roll < 0) {
                ChatterTopic topic = entry.getValue();
                return Optional.of(new Topic(entry.getKey(), topic.lines(), topic.tell(), false, topic.weight(), null));
            }
        }
        return Optional.empty();
    }
}
