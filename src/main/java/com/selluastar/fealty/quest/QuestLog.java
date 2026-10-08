package com.selluastar.fealty.quest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * A player's quests with one giver (a village, a named villager, a chain step, ...): the quests accepted and not
 * yet finished, today's offers, and which quests have been done since the pool last cycled.
 */
public final class QuestLog {
    public static final Codec<QuestLog> CODEC = RecordCodecBuilder.create(i -> i.group(
            ActiveQuest.CODEC.listOf().optionalFieldOf("actives", List.of()).forGetter(l -> List.copyOf(l.actives)),
            // Older saves kept a single quest here.
            ActiveQuest.CODEC.optionalFieldOf("active").forGetter(l -> Optional.empty()),
            ResourceLocation.CODEC.listOf().optionalFieldOf("completed", List.of()).forGetter(l -> List.copyOf(l.completed)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("offers", List.of()).forGetter(l -> List.copyOf(l.offers)),
            Codec.LONG.optionalFieldOf("offers_day", -1L).forGetter(l -> l.offersDay),
            Codec.INT.optionalFieldOf("total_completed", 0).forGetter(l -> l.totalCompleted)
    ).apply(i, (actives, legacy, completed, offers, day, total) -> {
        QuestLog log = new QuestLog();
        log.actives.addAll(actives);
        legacy.ifPresent(log.actives::add);
        log.completed.addAll(completed);
        log.offers.addAll(offers);
        log.offersDay = day;
        log.totalCompleted = total;
        return log;
    }));

    private final List<ActiveQuest> actives = new ArrayList<>();
    /** Quests done since the pool last cycled. */
    private final Set<ResourceLocation> completed = new HashSet<>();
    /** Today's offers still open (accepted ones are removed until the next day's roll). */
    private final List<ResourceLocation> offers = new ArrayList<>();
    private long offersDay = -1;
    private int totalCompleted;

    /** The accepted quests with this giver, oldest first. */
    public List<ActiveQuest> actives() {
        return actives;
    }

    /** The oldest accepted quest, if any. */
    @Nullable
    public ActiveQuest active() {
        return actives.isEmpty() ? null : actives.getFirst();
    }

    public boolean hasActive() {
        return !actives.isEmpty();
    }

    public void add(ActiveQuest quest) {
        actives.add(quest);
    }

    public boolean remove(UUID instance) {
        return actives.removeIf(q -> q.instanceId().equals(instance));
    }

    public Optional<ActiveQuest> find(ResourceLocation questId) {
        return actives.stream().filter(q -> q.questId().equals(questId)).findFirst();
    }

    public Optional<ActiveQuest> byInstance(UUID instance) {
        return actives.stream().filter(q -> q.instanceId().equals(instance)).findFirst();
    }

    public Set<ResourceLocation> completed() {
        return completed;
    }

    public List<ResourceLocation> offers() {
        return offers;
    }

    public long offersDay() {
        return offersDay;
    }

    public void setOffersDay(long offersDay) {
        this.offersDay = offersDay;
    }

    public int totalCompleted() {
        return totalCompleted;
    }

    public void incrementCompleted() {
        totalCompleted++;
    }
}
