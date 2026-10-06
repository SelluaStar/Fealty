package com.selluastar.fealty.guard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * A village's guards. Each slot is a post in Fealty's watch; the guard standing in it is only its current holder. The
 * village's other guards (iron golems, other mods' guards) are kept on the roster too, as last seen.
 * A guard who dies leaves the slot empty until a new one is sworn in, a set number of days later. A guard whose
 * slot has moved on (a new holder was sent to the lord, or sworn in after the old one was lost) leaves the world.
 */
public final class Garrison {
    public static final Codec<Garrison> CODEC = RecordCodecBuilder.create(i -> i.group(
            Slot.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(g -> g.slots),
            Codec.INT.optionalFieldOf("population", -1).forGetter(g -> g.population),
            Other.CODEC.listOf().optionalFieldOf("others", List.of()).forGetter(g -> List.copyOf(g.others.values()))
    ).apply(i, (slots, population, others) -> {
        Garrison g = new Garrison();
        g.slots.addAll(slots);
        g.population = population;
        others.forEach(o -> g.others.put(o.entity, o));
        return g;
    }));
    /** Other guards not seen for this many days are forgotten (they wandered off, or were taken away). */
    private static final int FORGET_UNSEEN_DAYS = 3;
    /** Fallen other guards stay on the roster this many days. */
    private static final int FORGET_FALLEN_DAYS = 2;

    private final List<Slot> slots = new ArrayList<>();
    private int population = -1;
    private final Map<UUID, Other> others = new LinkedHashMap<>();

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

    /** The village's guards that are not Fealty's: iron golems and other mods' guards, as last seen. */
    public Collection<Other> others() {
        return others.values();
    }

    public int othersAlive() {
        int alive = 0;
        for (Other other : others.values()) {
            if (other.diedDay < 0) {
                alive++;
            }
        }
        return alive;
    }

    /** Every guard of the village standing, Fealty's and others. */
    public int allAlive() {
        return alive() + othersAlive();
    }

    /** Every place in the watch: Fealty's posts and the other guards on the roster. */
    public int allTotal() {
        return slots.size() + others.size();
    }

    /** One of the village's other guards was seen today. @return whether the roster changed */
    public boolean noteOther(UUID entity, String kind, String name, long day) {
        Other other = others.get(entity);
        if (other == null) {
            others.put(entity, new Other(entity, kind, name, day, -1L));
            return true;
        }
        boolean changed = other.seenDay != day || !other.name.equals(name) || other.diedDay >= 0;
        other.seenDay = day;
        other.name = name;
        other.kind = kind;
        other.diedDay = -1;
        return changed;
    }

    /** @return whether the guard was on the roster */
    public boolean otherDied(UUID entity, long day) {
        Other other = others.get(entity);
        if (other == null) {
            return false;
        }
        other.diedDay = day;
        return true;
    }

    /** Forget the fallen after a while, and those not seen for days. @return whether the roster changed */
    public boolean forgetOthers(long day) {
        return others.values().removeIf(o -> o.diedDay >= 0 ? day - o.diedDay >= FORGET_FALLEN_DAYS : day - o.seenDay >= FORGET_UNSEEN_DAYS);
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

    /** A guard of the village that is not one of Fealty's: an iron golem, or another mod's guard. */
    public static final class Other {
        public static final Codec<Other> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("entity").forGetter(o -> o.entity),
                Codec.STRING.optionalFieldOf("kind", "guard").forGetter(o -> o.kind),
                Codec.STRING.optionalFieldOf("name", "").forGetter(o -> o.name),
                Codec.LONG.optionalFieldOf("seen_day", 0L).forGetter(o -> o.seenDay),
                Codec.LONG.optionalFieldOf("died_day", -1L).forGetter(o -> o.diedDay)
        ).apply(i, Other::new));

        private final UUID entity;
        private String kind;
        private String name;
        private long seenDay;
        private long diedDay;

        Other(UUID entity, String kind, String name, long seenDay, long diedDay) {
            this.entity = entity;
            this.kind = kind;
            this.name = name;
            this.seenDay = seenDay;
            this.diedDay = diedDay;
        }

        public UUID entity() {
            return entity;
        }

        /** {@code golem} or {@code guard}. */
        public String kind() {
            return kind;
        }

        /** Their name if they have one, else empty. */
        public String name() {
            return name;
        }

        /** The Fealty day they were last seen in or near the village. */
        public long seenDay() {
            return seenDay;
        }

        /** The Fealty day they fell, or -1. */
        public long diedDay() {
            return diedDay;
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

        /** A lord paid for a new guard: the post is filled at the next upkeep instead of after the waiting days. */
        public void readyNow() {
            this.diedDay = -1;
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
