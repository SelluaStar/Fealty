package com.selluastar.fealty.trade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/** What one villager remembers: threats and gifts per player, and uses of its village-exclusive trades. */
public final class VillagerMemory {
    public static final Codec<VillagerMemory> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Entry.CODEC).optionalFieldOf("players", Map.of()).forGetter(m -> m.entries),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("exclusive_uses", Map.of()).forGetter(m -> m.exclusiveUses),
            Codec.LONG.optionalFieldOf("exclusive_day", 0L).forGetter(m -> m.exclusiveDay)
    ).apply(i, VillagerMemory::new));

    private final Map<UUID, Entry> entries;
    private final Map<String, Integer> exclusiveUses;
    private long exclusiveDay;

    public VillagerMemory() {
        this(Map.of(), Map.of(), 0L);
    }

    private VillagerMemory(Map<UUID, Entry> entries, Map<String, Integer> exclusiveUses, long exclusiveDay) {
        this.entries = new HashMap<>(entries);
        this.exclusiveUses = new HashMap<>(exclusiveUses);
        this.exclusiveDay = exclusiveDay;
    }

    public Entry of(UUID player) {
        return entries.computeIfAbsent(player, k -> new Entry());
    }

    /** Uses of a village-exclusive trade today. Exclusive trades restock daily. */
    public int exclusiveUses(String key, long day) {
        if (day != exclusiveDay) {
            exclusiveDay = day;
            exclusiveUses.clear();
        }
        return exclusiveUses.getOrDefault(key, 0);
    }

    public void setExclusiveUses(String key, int uses) {
        exclusiveUses.put(key, uses);
    }

    public static final class Entry {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("last_threat", Long.MIN_VALUE / 2).forGetter(e -> e.lastThreat),
                Codec.INT.optionalFieldOf("threats", 0).forGetter(e -> e.threats),
                Codec.LONG.optionalFieldOf("refused_until", 0L).forGetter(e -> e.refusedUntil),
                Codec.BOOL.optionalFieldOf("threat_price", false).forGetter(e -> e.threatPrice),
                Codec.LONG.optionalFieldOf("last_gift", Long.MIN_VALUE / 2).forGetter(e -> e.lastGift),
                Codec.LONG.optionalFieldOf("last_gift_given", Long.MIN_VALUE / 2).forGetter(e -> e.lastGiftGiven),
                Codec.LONG.optionalFieldOf("last_pickpocket", Long.MIN_VALUE / 2).forGetter(e -> e.lastPickpocket)
        ).apply(i, (lastThreat, threats, refused, price, gift, given, pick) -> {
            Entry e = new Entry();
            e.lastThreat = lastThreat;
            e.threats = threats;
            e.refusedUntil = refused;
            e.threatPrice = price;
            e.lastGift = gift;
            e.lastGiftGiven = given;
            e.lastPickpocket = pick;
            return e;
        }));

        public long lastThreat = Long.MIN_VALUE / 2;
        public int threats;
        public long refusedUntil;
        /** The next trade happens at Neutral prices because the player threatened this villager. */
        public boolean threatPrice;
        public long lastGift = Long.MIN_VALUE / 2;
        public long lastGiftGiven = Long.MIN_VALUE / 2;
        public long lastPickpocket = Long.MIN_VALUE / 2;
    }
}
