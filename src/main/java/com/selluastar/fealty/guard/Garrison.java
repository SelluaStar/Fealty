package com.selluastar.fealty.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;

/**
 * A village's Fealty guards. Each slot is a post in the watch; the guard standing in it is only its current holder.
 * A guard who dies leaves the slot empty until a new one is sworn in, a set number of days later. A guard whose
 * slot has moved on (a new holder was sent to the lord, or sworn in after the old one was lost) leaves the world.
 */
public final class Garrison {
    public static final Codec<Garrison> CODEC = RecordCodecBuilder.create(i -> i.group(
            Slot.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(g -> g.slots),
            Codec.INT.optionalFieldOf("population", -1).forGetter(g -> g.population)
    ).apply(i, (slots, population) -> {
        Garrison g = new Garrison();
        g.slots.addAll(slots);
        g.population = population;
        return g;
    }));

    private final List<Slot> slots = new ArrayList<>();
    private int population = -1;

    public List<Slot> slots() {
        return slots;
    }

    /** Villagers counted when the village was last visited, or -1 if never counted. */
    public int population() {
        return population;
    }

    public void setPopulation(int population) {
        this.population = population;
    }

    public Optional<Slot> slot(int index) {
        return index >= 0 && index < slots.size() ? Optional.of(slots.get(index)) : Optional.empty();
    }

    public int alive() {
        int alive = 0;
        for (Slot slot : slots) {
            if (slot.diedDay < 0) {
                alive++;
            }
        }
        return alive;
    }

    public enum Rank implements StringRepresentable {
        SWORDSMAN("swordsman"),
        ARCHER("archer"),
        SERGEANT("sergeant");

        public static final Codec<Rank> CODEC = StringRepresentable.fromEnum(Rank::values);
        private final String name;

        Rank(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public static Rank byId(int id) {
            Rank[] values = values();
            return values[Math.floorMod(id, values.length)];
        }
    }

    /** One post in the watch. */
    public static final class Slot {
        public static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Rank.CODEC.optionalFieldOf("rank", Rank.SWORDSMAN).forGetter(s -> s.rank),
                UUIDUtil.CODEC.optionalFieldOf("entity").forGetter(s -> Optional.ofNullable(s.entity)),
                Codec.INT.optionalFieldOf("generation", 0).forGetter(s -> s.generation),
                Codec.LONG.optionalFieldOf("died_day", -1L).forGetter(s -> s.diedDay),
                BlockPos.CODEC.optionalFieldOf("last_pos").forGetter(s -> Optional.ofNullable(s.lastPos)),
                Level.RESOURCE_KEY_CODEC.optionalFieldOf("last_dimension").forGetter(s -> Optional.ofNullable(s.lastDimension)),
                Codec.STRING.optionalFieldOf("name", "").forGetter(s -> s.name)
        ).apply(i, (rank, entity, generation, died, pos, dim, name) -> {
            Slot s = new Slot(rank);
            s.entity = entity.orElse(null);
            s.generation = generation;
            s.diedDay = died;
            s.lastPos = pos.orElse(null);
            s.lastDimension = dim.orElse(null);
            s.name = name;
            return s;
        }));

        private final Rank rank;
        @Nullable
        private UUID entity;
        private int generation;
        private long diedDay = -1;
        @Nullable
        private BlockPos lastPos;
        @Nullable
        private ResourceKey<Level> lastDimension;
        private String name = "";
        /** Upkeep checks in a row that found the holder's last known place loaded but the holder missing (not saved). */
        int missing;

        public Slot(Rank rank) {
            this.rank = rank;
        }

        public Rank rank() {
            return rank;
        }

        /** The guard holding this slot now, if one is out in the world. */
        @Nullable
        public UUID entity() {
            return entity;
        }

        public int generation() {
            return generation;
        }

        /** The Fealty day the last holder died, or -1 when the slot is held or waiting to be filled. */
        public long diedDay() {
            return diedDay;
        }

        public boolean isDead() {
            return diedDay >= 0;
        }

        @Nullable
        public BlockPos lastPos() {
            return lastPos;
        }

        @Nullable
        public ResourceKey<Level> lastDimension() {
            return lastDimension;
        }

        /** The current holder's name, so the roster can name them while they are away. */
        public String name() {
            return name;
        }

        /** A new holder is out in the world. */
        public void fill(UUID entity, String name, ResourceKey<Level> dimension, BlockPos pos) {
            this.entity = entity;
            this.name = name;
            this.diedDay = -1;
            this.lastDimension = dimension;
            this.lastPos = pos.immutable();
            this.missing = 0;
        }

        /** The holder is gone (sent away, or lost); any old holder that turns up again is no longer this slot's. */
        public void vacate() {
            this.entity = null;
            this.generation++;
            this.missing = 0;
        }

        /** The holder died: the post stays empty for a while, then someone new takes it. */
        public void died(long day) {
            vacate();
            this.diedDay = day;
            this.name = "";
        }

        public void seen(ResourceKey<Level> dimension, BlockPos pos) {
            this.lastDimension = dimension;
            this.lastPos = pos.immutable();
            this.missing = 0;
        }
    }
}
