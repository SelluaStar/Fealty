package com.selluastar.fealty.village;

import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.FactionDefinition;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Works out which village owns a position. Villages are matched in this order:
 * <ol>
 *     <li>villages Fealty already knows about,</li>
 *     <li>structure starts in a village faction template's structure tag (default {@code #minecraft:village}),</li>
 *     <li>a bell (meeting point) for player-built villages and structures no tag covers.</li>
 * </ol>
 */
public final class VillageResolver {
    /** Chunks recently checked and found to hold no village, with the game time the result expires. */
    private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> NEGATIVE_CACHE = new java.util.HashMap<>();
    private static final long NEGATIVE_TTL = 1200;

    private VillageResolver() {
    }

    public static void clearCache() {
        NEGATIVE_CACHE.clear();
    }

    /** The village a villager belongs to: its meeting point's village, else the village it stands in. */
    public static Optional<VillageRecord> villageOf(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        Optional<GlobalPos> meeting = villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT);
        if (meeting.isPresent() && meeting.get().dimension().equals(level.dimension())) {
            Optional<VillageRecord> byBell = villageAt(level, meeting.get().pos());
            if (byBell.isPresent()) {
                return byBell;
            }
        }
        return villageAt(level, villager.blockPosition());
    }

    /** The village at a position, creating its record the first time it is found. */
    public static Optional<VillageRecord> villageAt(ServerLevel level, BlockPos pos) {
        FealtyWorldData data = FealtyWorldData.get(level.getServer());
        for (VillageRecord record : data.villages()) {
            if (record.contains(level.dimension(), pos)) {
                return Optional.of(record);
            }
        }
        long chunkKey = ChunkPos.asLong(pos);
        Long2LongOpenHashMap negative = NEGATIVE_CACHE.computeIfAbsent(level.dimension(), k -> new Long2LongOpenHashMap());
        long now = level.getGameTime();
        if (negative.get(chunkKey) > now) {
            return Optional.empty();
        }
        Optional<VillageRecord> found = findStructureVillage(level, pos, data);
        if (found.isEmpty()) {
            found = findBellVillage(level, pos, data);
        }
        if (found.isEmpty()) {
            negative.put(chunkKey, now + NEGATIVE_TTL);
        }
        return found;
    }

    /** The closest known village within a radius of a position (by centre), resolving the position first. */
    public static Optional<VillageRecord> nearestVillage(ServerLevel level, BlockPos pos, int radius) {
        Optional<VillageRecord> here = villageAt(level, pos);
        if (here.isPresent()) {
            return here;
        }
        VillageRecord best = null;
        double bestDistance = (double) radius * radius;
        for (VillageRecord record : FealtyWorldData.get(level.getServer()).villages()) {
            if (!record.dimension().equals(level.dimension())) {
                continue;
            }
            double d = record.center().distSqr(pos);
            if (d <= bestDistance) {
                bestDistance = d;
                best = record;
            }
        }
        return Optional.ofNullable(best);
    }

    /** A village record for a structure start, creating it if needed. Used to target villages that are not loaded. */
    public static Optional<VillageRecord> villageForStart(ServerLevel level, StructureStart start) {
        if (!start.isValid()) {
            return Optional.empty();
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Holder<Structure> holder = registry.wrapAsHolder(start.getStructure());
        for (Map.Entry<ResourceLocation, FactionDefinition> template : FealtyDataManager.villageTemplates()) {
            if (template.getValue().structures().map(holder::is).orElse(false)) {
                ResourceLocation id = structureVillageId(level, start.getChunkPos());
                FealtyWorldData data = FealtyWorldData.get(level.getServer());
                Optional<VillageRecord> existing = data.village(id);
                if (existing.isPresent()) {
                    return existing;
                }
                return Optional.of(createStructureVillage(level, start, holder, template.getKey(), template.getValue(), data));
            }
        }
        return Optional.empty();
    }

    private static Optional<VillageRecord> findStructureVillage(ServerLevel level, BlockPos pos, FealtyWorldData data) {
        if (FealtyDataManager.villageTemplates().isEmpty()) {
            return Optional.empty();
        }
        int margin = FealtyConfig.VILLAGE_MARGIN.get();
        int chunkRadius = Math.max(1, (margin + 15) / 16);
        ChunkPos center = new ChunkPos(pos);
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<Structure, LongSet> ref : chunk.getAllReferences().entrySet()) {
                    Holder<Structure> holder = registry.wrapAsHolder(ref.getKey());
                    Map.Entry<ResourceLocation, FactionDefinition> template = templateFor(holder);
                    if (template == null) {
                        continue;
                    }
                    for (long startChunk : ref.getValue()) {
                        ChunkPos startPos = new ChunkPos(startChunk);
                        ResourceLocation id = structureVillageId(level, startPos);
                        Optional<VillageRecord> existing = data.village(id);
                        if (existing.isPresent()) {
                            if (existing.get().contains(level.dimension(), pos)) {
                                return existing;
                            }
                            continue;
                        }
                        ChunkAccess access = level.getChunk(startPos.x, startPos.z, ChunkStatus.STRUCTURE_STARTS);
                        StructureStart start = access.getStartForStructure(ref.getKey());
                        if (start == null || !start.isValid()) {
                            continue;
                        }
                        if (start.getBoundingBox().inflatedBy(margin).isInside(pos)) {
                            return Optional.of(createStructureVillage(level, start, holder, template.getKey(), template.getValue(), data));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Nullable
    private static Map.Entry<ResourceLocation, FactionDefinition> templateFor(Holder<Structure> holder) {
        for (Map.Entry<ResourceLocation, FactionDefinition> template : FealtyDataManager.villageTemplates()) {
            if (template.getValue().structures().map(holder::is).orElse(false)) {
                return template;
            }
        }
        return null;
    }

    private static VillageRecord createStructureVillage(ServerLevel level, StructureStart start, Holder<Structure> holder,
                                                        ResourceLocation templateId, FactionDefinition template, FealtyWorldData data) {
        ResourceLocation id = structureVillageId(level, start.getChunkPos());
        BoundingBox box = start.getBoundingBox().inflatedBy(FealtyConfig.VILLAGE_MARGIN.get());
        BlockPos center = box.getCenter();
        Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), center,
                Math.max(box.getXSpan(), box.getZSpan()) / 2, PoiManager.Occupancy.ANY);
        if (bell.isPresent()) {
            center = bell.get();
        }
        boolean elder = template.elder() && holder.is(FealtyTags.Structures.ELDER_VILLAGES) && FealtyConfig.SPAWN_ELDERS.get();
        ResourceLocation structureId = holder.unwrapKey().map(ResourceKey::location).orElse(null);
        String name = VillageNames.villageName(level.getSeed() ^ id.hashCode());
        VillageRecord record = new VillageRecord(id, level.dimension(), center, box, templateId, structureId, name, elder);
        data.addVillage(record);
        return record;
    }

    private static Optional<VillageRecord> findBellVillage(ServerLevel level, BlockPos pos, FealtyWorldData data) {
        int radius = FealtyConfig.BELL_VILLAGE_RADIUS.get();
        Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), pos, radius, PoiManager.Occupancy.ANY);
        if (bell.isEmpty()) {
            return Optional.empty();
        }
        BlockPos bellPos = bell.get();
        // The bell might belong to a structure village whose margin did not reach this position.
        Optional<VillageRecord> structureVillage = findStructureVillage(level, bellPos, data);
        if (structureVillage.isPresent()) {
            return structureVillage;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Factions.VILLAGE_NAMESPACE,
                dimensionPath(level) + "/bell_" + bellPos.getX() + "_" + bellPos.getY() + "_" + bellPos.getZ());
        Optional<VillageRecord> existing = data.village(id);
        if (existing.isPresent()) {
            return existing;
        }
        BoundingBox box = new BoundingBox(bellPos.getX() - radius, bellPos.getY() - radius / 2, bellPos.getZ() - radius,
                bellPos.getX() + radius, bellPos.getY() + radius / 2, bellPos.getZ() + radius);
        boolean elder = FealtyConfig.BELL_VILLAGES_HAVE_ELDERS.get() && FealtyConfig.SPAWN_ELDERS.get();
        String name = VillageNames.villageName(level.getSeed() ^ id.hashCode());
        VillageRecord record = new VillageRecord(id, level.dimension(), bellPos, box, Factions.VILLAGE_TEMPLATE, null, name, elder);
        data.addVillage(record);
        return Optional.of(record);
    }

    public static ResourceLocation structureVillageId(ServerLevel level, ChunkPos startChunk) {
        return ResourceLocation.fromNamespaceAndPath(Factions.VILLAGE_NAMESPACE,
                dimensionPath(level) + "/" + startChunk.x + "_" + startChunk.z);
    }

    private static String dimensionPath(ServerLevel level) {
        ResourceLocation dim = level.dimension().location();
        return dim.getNamespace() + "/" + dim.getPath();
    }
}
