package com.selluastar.fealty.rep;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-wide Fealty state, stored with the overworld ({@code data/fealty.dat}). */
public final class FealtyWorldData extends SavedData {
    private static final String NAME = "fealty";
    private static final Codec<Map<UUID, PlayerRepData>> PLAYERS_CODEC = Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerRepData.CODEC);
    private static final Codec<Map<ResourceLocation, VillageRecord>> VILLAGES_CODEC = Codec.unboundedMap(ResourceLocation.CODEC, VillageRecord.CODEC);

    private static final SavedData.Factory<FealtyWorldData> FACTORY =
            new SavedData.Factory<>(FealtyWorldData::new, FealtyWorldData::load, null);

    private final Map<UUID, PlayerRepData> players = new HashMap<>();
    private final Map<ResourceLocation, VillageRecord> villages = new LinkedHashMap<>();
    /** Fealty's own day counter, see {@link FealtyCalendar}. */
    long calendarDay;
    long calendarLastIndex = Long.MIN_VALUE;
    long calendarLastAdvance;
    boolean calendarStarted;

    public static FealtyWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static FealtyWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        FealtyWorldData data = new FealtyWorldData();
        if (tag.contains("players")) {
            PLAYERS_CODEC.parse(NbtOps.INSTANCE, tag.get("players"))
                    .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to load player data: {}", e))
                    .ifPresent(data.players::putAll);
        }
        if (tag.contains("villages")) {
            VILLAGES_CODEC.parse(NbtOps.INSTANCE, tag.get("villages"))
                    .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to load village data: {}", e))
                    .ifPresent(data.villages::putAll);
        }
        if (tag.contains("calendar")) {
            CompoundTag calendar = tag.getCompound("calendar");
            data.calendarDay = calendar.getLong("day");
            data.calendarLastIndex = calendar.getLong("last_index");
            data.calendarLastAdvance = calendar.getLong("last_advance");
            data.calendarStarted = true;
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        PLAYERS_CODEC.encodeStart(NbtOps.INSTANCE, players)
                .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to save player data: {}", e))
                .ifPresent(t -> tag.put("players", t));
        VILLAGES_CODEC.encodeStart(NbtOps.INSTANCE, villages)
                .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to save village data: {}", e))
                .ifPresent(t -> tag.put("villages", t));
        if (calendarStarted) {
            CompoundTag calendar = new CompoundTag();
            calendar.putLong("day", calendarDay);
            calendar.putLong("last_index", calendarLastIndex);
            calendar.putLong("last_advance", calendarLastAdvance);
            tag.put("calendar", calendar);
        }
        return tag;
    }

    public PlayerRepData player(UUID uuid) {
        return players.computeIfAbsent(uuid, k -> {
            setDirty();
            return new PlayerRepData();
        });
    }

    public Optional<PlayerRepData> existingPlayer(UUID uuid) {
        return Optional.ofNullable(players.get(uuid));
    }

    public Map<UUID, PlayerRepData> players() {
        return players;
    }

    public Optional<VillageRecord> village(ResourceLocation id) {
        return Optional.ofNullable(villages.get(id));
    }

    public Collection<VillageRecord> villages() {
        return villages.values();
    }

    public void addVillage(VillageRecord record) {
        villages.put(record.id(), record);
        setDirty();
    }

}
