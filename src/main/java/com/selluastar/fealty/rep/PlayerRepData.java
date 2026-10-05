package com.selluastar.fealty.rep;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.chain.ChainProgress;
import com.selluastar.fealty.quest.QuestLog;

import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/** Everything Fealty remembers about one player. Lives in {@link FealtyWorldData}, so it works while offline. */
public final class PlayerRepData {
    public static final Codec<PlayerRepData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("rep", Map.of()).forGetter(d -> d.rep),
            Codec.DOUBLE.optionalFieldOf("renown", 0.0).forGetter(d -> d.renown),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("heat", Map.of()).forGetter(d -> d.heat),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf("wanted", Map.of()).forGetter(d -> d.wanted),
            Codec.unboundedMap(Codec.STRING, Tally.CODEC).optionalFieldOf("tallies", Map.of()).forGetter(d -> d.tallies),
            Codec.unboundedMap(ResourceLocation.CODEC, QuestLog.CODEC).optionalFieldOf("quests", Map.of()).forGetter(d -> d.quests),
            Codec.unboundedMap(ResourceLocation.CODEC, ChainProgress.CODEC).optionalFieldOf("chains", Map.of()).forGetter(d -> d.chains),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG).optionalFieldOf("aggro", Map.of()).forGetter(d -> d.aggroUntil),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG).optionalFieldOf("warned", Map.of()).forGetter(d -> d.warnedAt),
            Codec.LONG.optionalFieldOf("next_bounty", 0L).forGetter(d -> d.nextBountyTime),
            ResourceLocation.CODEC.listOf().optionalFieldOf("flags", List.of()).forGetter(d -> List.copyOf(d.flags)),
            Codec.STRING.optionalFieldOf("name", "").forGetter(d -> d.name),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("untracked_quests", List.of()).forGetter(d -> List.copyOf(d.untracked)),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("stats", Map.of()).forGetter(d -> d.stats)
    ).apply(i, (rep, renown, heat, wanted, tallies, quests, chains, aggro, warned, nextBounty, flags, name, untracked, stats) -> {
        PlayerRepData d = new PlayerRepData();
        d.rep.putAll(rep);
        d.renown = renown;
        d.heat.putAll(heat);
        d.wanted.putAll(wanted);
        d.tallies.putAll(tallies);
        d.quests.putAll(quests);
        d.chains.putAll(chains);
        d.aggroUntil.putAll(aggro);
        d.warnedAt.putAll(warned);
        d.nextBountyTime = nextBounty;
        d.flags.addAll(flags);
        d.name = name;
        d.untracked.addAll(untracked);
        d.stats.putAll(stats);
        return d;
    }));

    final Map<ResourceLocation, Integer> rep = new HashMap<>();
    double renown;
    final Map<ResourceLocation, Integer> heat = new HashMap<>();
    final Map<ResourceLocation, Integer> wanted = new HashMap<>();
    final Map<String, Tally> tallies = new HashMap<>();
    final Map<ResourceLocation, QuestLog> quests = new HashMap<>();
    final Map<ResourceLocation, ChainProgress> chains = new HashMap<>();
    final Map<ResourceLocation, Long> aggroUntil = new HashMap<>();
    final Map<ResourceLocation, Long> warnedAt = new HashMap<>();
    long nextBountyTime;
    final Set<ResourceLocation> flags = new HashSet<>();
    String name = "";
    final Set<UUID> untracked = new HashSet<>();
    final Map<String, Integer> stats = new HashMap<>();

    public Map<ResourceLocation, Integer> rep() {
        return rep;
    }

    public double renown() {
        return renown;
    }

    public void setRenown(double renown) {
        this.renown = renown;
    }

    public Map<ResourceLocation, Integer> heat() {
        return heat;
    }

    public Map<ResourceLocation, Integer> wanted() {
        return wanted;
    }

    public QuestLog quests(ResourceLocation giver) {
        return quests.computeIfAbsent(giver, k -> new QuestLog());
    }

    public Map<ResourceLocation, QuestLog> allQuests() {
        return quests;
    }

    public Map<ResourceLocation, ChainProgress> chains() {
        return chains;
    }

    public Map<ResourceLocation, Long> aggroUntil() {
        return aggroUntil;
    }

    public Map<ResourceLocation, Long> warnedAt() {
        return warnedAt;
    }

    public long nextBountyTime() {
        return nextBountyTime;
    }

    public void setNextBountyTime(long time) {
        this.nextBountyTime = time;
    }

    public boolean hasFlag(ResourceLocation flag) {
        return flags.contains(flag);
    }

    /** @return true if the flag was newly set */
    public boolean setFlag(ResourceLocation flag) {
        return flags.add(flag);
    }

    public void clearFlag(ResourceLocation flag) {
        flags.remove(flag);
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** Whether an accepted quest shows in the HUD tracker. Quests are tracked unless the player hides them. */
    public boolean isTracked(UUID quest) {
        return !untracked.contains(quest);
    }

    public void setTracked(UUID quest, boolean tracked) {
        if (tracked) {
            untracked.remove(quest);
        } else {
            untracked.add(quest);
        }
    }

    /** Forget tracking choices for quests that no longer exist. */
    public void retainTracked(Set<UUID> live) {
        untracked.retainAll(live);
    }

    /** Lifetime counters shown in the journal (quests done, gifts given, crimes seen, ...). */
    public int stat(String key) {
        return stats.getOrDefault(key, 0);
    }

    public void addStat(String key, int amount) {
        stats.merge(key, amount, Integer::sum);
    }

    public Map<String, Integer> stats() {
        return stats;
    }

    public Tally tally(ResourceLocation faction, ResourceLocation source, long day) {
        Tally t = tallies.computeIfAbsent(faction + "|" + source, k -> new Tally(day, 0, 0));
        if (t.day != day) {
            t.day = day;
            t.count = 0;
            t.applied = 0;
        }
        return t;
    }

    /** Daily counter for capped rep sources such as trading. */
    public static final class Tally {
        static final Codec<Tally> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(t -> t.day),
                Codec.INT.fieldOf("count").forGetter(t -> t.count),
                Codec.INT.fieldOf("applied").forGetter(t -> t.applied)
        ).apply(i, Tally::new));

        long day;
        int count;
        int applied;

        Tally(long day, int count, int applied) {
            this.day = day;
            this.count = count;
            this.applied = applied;
        }
    }
}
