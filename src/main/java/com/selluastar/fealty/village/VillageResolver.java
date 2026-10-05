package com.selluastar.fealty.village;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.FactionDefinition;
import com.selluastar.fealty.data.FealtyDataManager;
import com.selluastar.fealty.registry.ModAttachments;
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
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

/**
 * Works out which village owns a position. Villages are matched in this order:
 * <ol>
 *     <li>villages Fealty already knows about (including ones made by command),</li>
 *     <li>structure starts matched by a village template (tags, ids or globs, so modded towns and castles count),
 *     plus the {@code extra_village_structures} config,</li>
 *     <li>a bell (meeting point), for player-built villages and structures nothing matches,</li>
 *     <li>a settlement: a cluster of villager homes with villagers and a workstation, for bell-less modded villages.</li>
 * </ol>
 */
public final class VillageResolver {
    /** Chunks recently checked and found to hold no village, with the game time the result expires. */
    private static final Map<ResourceKey<Level>, Long2LongOpenHashMap> NEGATIVE_CACHE = new java.util.HashMap<>();
    private static final long NEGATIVE_TTL = 1200;
    /** Which template each structure type matches, worked out once per data pack load. */
    private static final Map<Structure, Optional<Map.Entry<ResourceLocation, FactionDefinition>>> TEMPLATE_CACHE = new IdentityHashMap<>();
    private static final int SETTLEMENT_SEARCH = 32;

    private VillageResolver() {
    }

    public static void clearCache() {
        NEGATIVE_CACHE.clear();
        TEMPLATE_CACHE.clear();
    }

    public static void clearTemplateCache() {
        TEMPLATE_CACHE.clear();
        NEGATIVE_CACHE.clear();
    }

    // ---- Membership ----

    /**
     * The village a villager belongs to: its bell's village, else its home's, else the village it was last known to
     * belong to, else the village it stands in. The answer is remembered on the villager so it keeps belonging
     * when it wanders out of the village bounds.
     */
    public static Optional<VillageRecord> villageOf(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        Optional<VillageRecord> found = byMemory(level, villager, MemoryModuleType.MEETING_POINT);
        if (found.isEmpty()) {
            found = byMemory(level, villager, MemoryModuleType.HOME);
        }
        if (found.isEmpty() && villager.hasData(ModAttachments.FACTION)) {
            found = FealtyWorldData.get(level.getServer()).village(villager.getData(ModAttachments.FACTION));
        }
        if (found.isEmpty()) {
            found = villageAt(level, villager.blockPosition());
        }
        found.ifPresent(record -> {
            if (!villager.hasData(ModAttachments.FACTION) || !villager.getData(ModAttachments.FACTION).equals(record.id())) {
                villager.setData(ModAttachments.FACTION, record.id());
            }
        });
        return found;
    }

    private static Optional<VillageRecord> byMemory(ServerLevel level, Villager villager, MemoryModuleType<GlobalPos> memory) {
        Optional<GlobalPos> pos = villager.getBrain().getMemory(memory);
        if (pos.isPresent() && pos.get().dimension().equals(level.dimension())) {
            return villageAt(level, pos.get().pos());
        }
        return Optional.empty();
    }

    // ---- Lookup ----

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
            found = findSettlement(level, pos, data);
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
        Holder<Structure> holder = structureRegistry(level).wrapAsHolder(start.getStructure());
        Map.Entry<ResourceLocation, FactionDefinition> template = templateFor(start.getStructure(), holder);
        if (template == null) {
            return Optional.empty();
        }
        ResourceLocation id = structureVillageId(level, start.getChunkPos());
        FealtyWorldData data = FealtyWorldData.get(level.getServer());
        Optional<VillageRecord> existing = data.village(id);
        if (existing.isPresent()) {
            return existing;
        }
        return Optional.of(createStructureVillage(level, start, holder, template.getKey(), template.getValue(), data));
    }

    // ---- Templates ----

    private static Registry<Structure> structureRegistry(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE);
    }

    /** The village template a structure matches, if any. Config exclusions win; config extras use the default template. */
    @Nullable
    public static Map.Entry<ResourceLocation, FactionDefinition> templateFor(Structure structure, Holder<Structure> holder) {
        return TEMPLATE_CACHE.computeIfAbsent(structure, s -> Optional.ofNullable(findTemplate(holder))).orElse(null);
    }

    @Nullable
    private static Map.Entry<ResourceLocation, FactionDefinition> findTemplate(Holder<Structure> holder) {
        if (StructureMatcher.matchesAny(FealtyConfig.EXCLUDED_VILLAGE_STRUCTURES.get(), holder)) {
            return null;
        }
        for (Map.Entry<ResourceLocation, FactionDefinition> template : FealtyDataManager.villageTemplates()) {
            if (template.getValue().structures().map(m -> m.matches(holder)).orElse(false)) {
                return template;
            }
        }
        if (StructureMatcher.matchesAny(FealtyConfig.EXTRA_VILLAGE_STRUCTURES.get(), holder)) {
            Optional<FactionDefinition> fallback = FealtyDataManager.faction(Factions.VILLAGE_TEMPLATE);
            if (fallback.isPresent()) {
                return Map.entry(Factions.VILLAGE_TEMPLATE, fallback.get());
            }
            if (!FealtyDataManager.villageTemplates().isEmpty()) {
                return FealtyDataManager.villageTemplates().getLast();
            }
        }
        return null;
    }

    /** Whether a village should have a trusting elder under the current tags and config. */
    public static boolean elderFlag(ServerLevel level, VillageRecord record) {
        if (!FealtyConfig.SPAWN_ELDERS.get()) {
            return false;
        }
        if (record.sites().manual()) {
            return true;
        }
        if (record.structure() == null) {
            return FealtyConfig.BELL_VILLAGES_HAVE_ELDERS.get();
        }
        boolean templateElder = FealtyDataManager.faction(record.template()).map(FactionDefinition::elder).orElse(true);
        if (!templateElder) {
            return false;
        }
        if (FealtyConfig.ELDERS_ONLY_IN_TAGGED_VILLAGES.get()) {
            return structureRegistry(level).getHolder(ResourceKey.create(Registries.STRUCTURE, record.structure()))
                    .map(h -> h.is(FealtyTags.Structures.ELDER_VILLAGES)).orElse(false);
        }
        return true;
    }

    // ---- Finding villages ----

    private static Optional<VillageRecord> findStructureVillage(ServerLevel level, BlockPos pos, FealtyWorldData data) {
        int margin = FealtyConfig.VILLAGE_MARGIN.get();
        int chunkRadius = Math.max(1, (margin + 15) / 16);
        ChunkPos center = new ChunkPos(pos);
        Registry<Structure> registry = structureRegistry(level);
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<Structure, LongSet> ref : chunk.getAllReferences().entrySet()) {
                    Holder<Structure> holder = registry.wrapAsHolder(ref.getKey());
                    Map.Entry<ResourceLocation, FactionDefinition> template = templateFor(ref.getKey(), holder);
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

    private static VillageRecord createStructureVillage(ServerLevel level, StructureStart start, Holder<Structure> holder,
                                                        ResourceLocation templateId, FactionDefinition template, FealtyWorldData data) {
        ResourceLocation id = structureVillageId(level, start.getChunkPos());
        BoundingBox box = start.getBoundingBox().inflatedBy(FealtyConfig.VILLAGE_MARGIN.get());
        BlockPos center = box.getCenter();
        Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), box::isInside, center,
                Math.max(box.getXSpan(), box.getZSpan()) / 2, PoiManager.Occupancy.ANY);
        if (bell.isPresent()) {
            center = bell.get();
        }
        ResourceLocation structureId = holder.unwrapKey().map(ResourceKey::location).orElse(null);
        String name = VillageNames.villageName(level.getSeed() ^ id.hashCode());
        VillageRecord record = new VillageRecord(id, level.dimension(), center, box, templateId, structureId, name, false);
        record.setHasElder(elderFlag(level, record));
        data.addVillage(record);
        Fealty.LOGGER.debug("Fealty: found village {} ({}) from structure {}", name, id, structureId);
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
        String name = VillageNames.villageName(level.getSeed() ^ id.hashCode());
        VillageRecord record = new VillageRecord(id, level.dimension(), bellPos, box, Factions.VILLAGE_TEMPLATE, null, name, false);
        record.setHasElder(elderFlag(level, record));
        record.sites().setRefined(true);
        data.addVillage(record);
        return Optional.of(record);
    }

    /** A cluster of villager homes with villagers and a workstation: a village with no bell and no known structure. */
    private static Optional<VillageRecord> findSettlement(ServerLevel level, BlockPos pos, FealtyWorldData data) {
        if (!FealtyConfig.DETECT_SETTLEMENTS.get()) {
            return Optional.empty();
        }
        PoiManager poi = level.getPoiManager();
        List<BlockPos> homes = poi.findAll(h -> h.is(PoiTypes.HOME), p -> true, pos, SETTLEMENT_SEARCH, PoiManager.Occupancy.ANY).toList();
        if (homes.size() < FealtyConfig.SETTLEMENT_MIN_HOMES.get()) {
            return Optional.empty();
        }
        if (poi.findAll(h -> h.is(PoiTypeTags.ACQUIRABLE_JOB_SITE), p -> true, pos, SETTLEMENT_SEARCH, PoiManager.Occupancy.ANY)
                .findAny().isEmpty()) {
            return Optional.empty();
        }
        AABB area = new AABB(pos).inflate(SETTLEMENT_SEARCH);
        if (level.getEntitiesOfClass(Villager.class, area, v -> !v.isBaby()).size() < 2) {
            return Optional.empty();
        }
        BlockPos center = centroid(homes);
        for (VillageRecord record : data.villages()) {
            if (record.contains(level.dimension(), center)) {
                return Optional.of(record);
            }
        }
        int radius = FealtyConfig.SETTLEMENT_RADIUS.get();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Factions.VILLAGE_NAMESPACE,
                dimensionPath(level) + "/settlement_" + center.getX() + "_" + center.getZ());
        BoundingBox box = new BoundingBox(center.getX() - radius, center.getY() - 24, center.getZ() - radius,
                center.getX() + radius, center.getY() + 24, center.getZ() + radius);
        String name = VillageNames.villageName(level.getSeed() ^ id.hashCode());
        VillageRecord record = new VillageRecord(id, level.dimension(), center, box, Factions.VILLAGE_TEMPLATE, null, name, false);
        record.setHasElder(elderFlag(level, record));
        record.sites().setRefined(true);
        data.addVillage(record);
        Fealty.LOGGER.debug("Fealty: found settlement {} at {}", name, center);
        return Optional.of(record);
    }

    private static BlockPos centroid(List<BlockPos> points) {
        long x = 0;
        long y = 0;
        long z = 0;
        for (BlockPos p : points) {
            x += p.getX();
            y += p.getY();
            z += p.getZ();
        }
        int n = points.size();
        return new BlockPos((int) (x / n), (int) (y / n), (int) (z / n));
    }

    // ---- Refinement and commands ----

    /**
     * Re-check a structure village once a player is in it: find the bell if it was not loaded when the village was
     * first found, else centre it on its homes and workstations, else on the ground at the middle of the structure.
     * The elder flag follows the current tags and config until an elder has been placed.
     */
    public static void refresh(ServerLevel level, VillageRecord record) {
        if (record.sites().refined() || !record.dimension().equals(level.dimension()) || !level.isLoaded(record.center())) {
            return;
        }
        BoundingBox box = record.bounds();
        BlockPos boxCenter = box.getCenter();
        int radius = Math.max(box.getXSpan(), box.getZSpan()) / 2;
        PoiManager poi = level.getPoiManager();
        BlockPos center = null;
        Optional<BlockPos> bell = poi.findClosest(h -> h.is(PoiTypes.MEETING), box::isInside, boxCenter, radius, PoiManager.Occupancy.ANY);
        if (bell.isPresent()) {
            center = bell.get();
        } else {
            List<BlockPos> places = new ArrayList<>(poi.findAll(h -> h.is(PoiTypes.HOME) || h.is(PoiTypeTags.ACQUIRABLE_JOB_SITE),
                    box::isInside, boxCenter, radius, PoiManager.Occupancy.ANY).toList());
            if (places.size() >= 2) {
                center = centroid(places);
            } else if (level.isLoaded(boxCenter)) {
                center = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, boxCenter);
            }
        }
        if (center != null && !record.isInitialized()) {
            record.setCenter(center);
        }
        if (record.elder().state() == VillageRecord.ElderState.NONE && !record.isInitialized()) {
            record.setHasElder(elderFlag(level, record));
        }
        record.sites().setRefined(true);
        FealtyWorldData.get(level.getServer()).setDirty();
    }

    /** Make a village by hand (castles and towns nothing detects). */
    public static VillageRecord createManual(ServerLevel level, BlockPos center, String name, int radius) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Factions.VILLAGE_NAMESPACE,
                dimensionPath(level) + "/manual_" + center.getX() + "_" + center.getZ());
        BoundingBox box = new BoundingBox(center.getX() - radius, center.getY() - Math.max(24, radius / 2), center.getZ() - radius,
                center.getX() + radius, center.getY() + Math.max(24, radius / 2), center.getZ() + radius);
        VillageRecord record = new VillageRecord(id, level.dimension(), center, box, Factions.VILLAGE_TEMPLATE, null, name, false);
        record.sites().setManual(true);
        record.sites().setRefined(true);
        record.setHasElder(elderFlag(level, record));
        FealtyWorldData.get(level.getServer()).addVillage(record);
        NEGATIVE_CACHE.clear();
        return record;
    }

    /** What the structures at a position are and whether they count as villages, for {@code /rep village scan}. */
    public static List<String> scan(ServerLevel level, BlockPos pos) {
        List<String> lines = new ArrayList<>();
        Registry<Structure> registry = structureRegistry(level);
        ChunkPos center = new ChunkPos(pos);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                if (chunk == null) {
                    continue;
                }
                for (Map.Entry<Structure, LongSet> ref : chunk.getAllReferences().entrySet()) {
                    Holder<Structure> holder = registry.wrapAsHolder(ref.getKey());
                    String id = holder.unwrapKey().map(k -> k.location().toString()).orElse("?");
                    if (!seen.add(id)) {
                        continue;
                    }
                    Map.Entry<ResourceLocation, FactionDefinition> template = templateFor(ref.getKey(), holder);
                    lines.add(id + " -> " + (template == null ? "not a village" : "village (template " + template.getKey() + ")"));
                }
            }
        }
        PoiManager poi = level.getPoiManager();
        long bells = poi.findAll(h -> h.is(PoiTypes.MEETING), p -> true, pos, 48, PoiManager.Occupancy.ANY).count();
        long homes = poi.findAll(h -> h.is(PoiTypes.HOME), p -> true, pos, SETTLEMENT_SEARCH, PoiManager.Occupancy.ANY).count();
        lines.add("bells within 48: " + bells + ", villager beds within " + SETTLEMENT_SEARCH + ": " + homes);
        return lines;
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
