package com.selluastar.fealty.quest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/** A player's quest history with one giver (usually a village). */
public final class QuestLog {
    public static final Codec<QuestLog> CODEC = RecordCodecBuilder.create(i -> i.group(
            ActiveQuest.CODEC.optionalFieldOf("active").forGetter(l -> Optional.ofNullable(l.active)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("completed", List.of()).forGetter(l -> List.copyOf(l.completed)),
            ResourceLocation.CODEC.listOf().optionalFieldOf("offers", List.of()).forGetter(l -> List.copyOf(l.offers)),
            Codec.LONG.optionalFieldOf("offers_day", -1L).forGetter(l -> l.offersDay),
            Codec.INT.optionalFieldOf("total_completed", 0).forGetter(l -> l.totalCompleted)
    ).apply(i, (active, completed, offers, day, total) -> {
        QuestLog log = new QuestLog();
        log.active = active.orElse(null);
        log.completed.addAll(completed);
        log.offers.addAll(offers);
        log.offersDay = day;
        log.totalCompleted = total;
        return log;
    }));

    @Nullable
    private ActiveQuest active;
    /** Quests done since the pool last cycled. */
    private final Set<ResourceLocation> completed = new HashSet<>();
    private final List<ResourceLocation> offers = new ArrayList<>();
    private long offersDay = -1;
    private int totalCompleted;

    @Nullable
    public ActiveQuest active() {
        return active;
    }

    public void setActive(@Nullable ActiveQuest active) {
        this.active = active;
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
