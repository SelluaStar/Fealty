package com.selluastar.fealty.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.mixin.ListPoolElementAccessor;
import com.selluastar.fealty.mixin.SinglePoolElementAccessor;
import com.selluastar.fealty.mixin.TemplateStructurePieceAccessor;
import com.selluastar.fealty.util.SpawnSpots;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;

/**
 * Picks spots in a village for the things Fealty adds: the elder's home, the coffer, the mailbox and the guard
 * post. It works from the structure's own pieces where it can, ranking them with the village's
 * {@link VillageLayout} and with what is inside them (beds, size, closeness to the middle), so it copes with
 * modded towns and castles as well as vanilla villages.
 */
public final class SitePlanner {
    private SitePlanner() {
    }

    /** One piece of a village's structure: its box and the template it was built from, if known. */
    public record Building(BoundingBox box, @Nullable ResourceLocation template) {
    }

    /** The structure start a village was found from, if it is still there. */
    public static Optional<StructureStart> start(ServerLevel level, VillageRecord record) {
        Optional<ChunkPos> startChunk = record.startChunk();
        if (record.structure() == null || startChunk.isEmpty()) {
            return Optional.empty();
        }
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(record.structure());
        if (structure == null) {
            return Optional.empty();
        }
        ChunkAccess access = level.getChunk(startChunk.get().x, startChunk.get().z, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = access.getStartForStructure(structure);
        return start != null && start.isValid() ? Optional.of(start) : Optional.empty();
    }

    /**
     * Where to look for a structure village's middle: the first piece, which for jigsaw villages is the town centre
     * (the well, the square or the keep), else the middle of the whole structure.
     */
    public static BlockPos heart(StructureStart start) {
        List<StructurePiece> pieces = start.getPieces();
        return pieces.isEmpty() ? start.getBoundingBox().getCenter() : pieces.getFirst().getBoundingBox().getCenter();
    }

    /** The template a piece was built from, for jigsaw pieces and plain template pieces. */
    public static Optional<ResourceLocation> template(StructurePiece piece) {
        if (piece instanceof PoolElementStructurePiece pool) {
            return template(pool.getElement());
        }
        if ((Object) piece instanceof TemplateStructurePieceAccessor templated) {
            return Optional.ofNullable(ResourceLocation.tryParse(templated.fealty$getTemplateName()));
        }
        return Optional.empty();
    }

    /** The template a jigsaw element places; a list element gives its first part's. */
    public static Optional<ResourceLocation> template(StructurePoolElement element) {
        if ((Object) element instanceof SinglePoolElementAccessor single) {
            return single.fealty$getTemplate().left();
        }
        if ((Object) element instanceof ListPoolElementAccessor list) {
            for (StructurePoolElement part : list.fealty$getElements()) {
                Optional<ResourceLocation> id = template(part);
                if (id.isPresent()) {
                    return id;
                }
            }
        }
        return Optional.empty();
    }

    /** The structure's buildings, largest first. Flat pieces such as roads are left out. */
    public static List<Building> buildings(ServerLevel level, VillageRecord record) {
        Optional<StructureStart> start = start(level, record);
        if (start.isEmpty()) {
            return List.of();
        }
        List<Building> buildings = new ArrayList<>();
        for (StructurePiece piece : start.get().getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (box.getYSpan() >= 4 && box.getXSpan() >= 4 && box.getZSpan() >= 4) {
                buildings.add(new Building(box, template(piece).orElse(null)));
            }
        }
        buildings.sort(Comparator.comparingLong((Building b) -> volume(b.box())).reversed());
        return buildings;
    }

    /**
     * How good a home a building would make for the elder, or empty if it must not be one. The village's
     * {@link VillageLayout} ranks buildings by name; beds inside and closeness to the middle count too, so towns
     * whose buildings do not match their names (or have no layout at all) still get a sensible pick.
     */
    static OptionalDouble buildingScore(ServerLevel level, VillageLayout layout, Building building, BlockPos center) {
        double distance = Math.sqrt(building.box().getCenter().distSqr(center));
        if (distance > layout.reach()) {
            return OptionalDouble.empty();
        }
        double score = -distance / 4.0;
        if (building.template() != null) {
            if (layout.avoids(building.template())) {
                return OptionalDouble.empty();
            }
            Optional<Integer> rank = layout.elderRank(building.template());
            if (rank.isPresent()) {
                score += Math.max(12, 40 - rank.get() * 6);
            }
        }
        score += Math.min(4, beds(level, building.box())) * 3;
        score += Math.min(8, volume(building.box()) / 1000);
        return OptionalDouble.of(score);
    }

    /** An indoor spot for the elder: in the best building near the middle, with a roof, near beds, off the roads. */
    @Nullable
    public static BlockPos elderHome(ServerLevel level, VillageRecord record) {
        BlockPos center = record.center();
        RandomSource random = level.getRandom();
        VillageLayout layout = VillageLayouts.forVillage(level, record);
        List<Building> buildings = buildings(level, record);
        List<Map.Entry<Building, Double>> ranked = new ArrayList<>();
        List<BoundingBox> avoided = new ArrayList<>();
        for (Building building : buildings) {
            OptionalDouble score = buildingScore(level, layout, building, center);
            if (score.isPresent()) {
                ranked.add(Map.entry(building, score.getAsDouble()));
            } else if (building.template() != null && layout.avoids(building.template())) {
                avoided.add(building.box());
            }
        }
        ranked.sort(Map.Entry.<Building, Double>comparingByValue().reversed());
        if (ranked.size() > 6) {
            ranked = ranked.subList(0, 6);
        }
        List<BlockPos> candidates = new ArrayList<>();
        for (Map.Entry<Building, Double> entry : ranked) {
            BoundingBox box = entry.getKey().box();
            for (int i = 0; i < 100; i++) {
                candidates.add(new BlockPos(
                        box.minX() + 1 + random.nextInt(Math.max(1, box.getXSpan() - 2)),
                        box.minY() + random.nextInt(Math.max(1, box.getYSpan())),
                        box.minZ() + 1 + random.nextInt(Math.max(1, box.getZSpan() - 2))));
            }
        }
        // Spots around the middle as well, found column by column so hillside and mountain villages work too.
        int reach = Math.min(32, Math.max(20, Math.max(record.bounds().getXSpan(), record.bounds().getZSpan()) / 4));
        for (int i = 0; i < 300; i++) {
            int x = center.getX() + random.nextInt(reach * 2 + 1) - reach;
            int z = center.getZ() + random.nextInt(reach * 2 + 1) - reach;
            SpawnSpots.nearY(level, x, z, center.getY(), 12).ifPresent(candidates::add);
        }
        BlockPos best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (BlockPos pos : candidates) {
            if (!record.contains(level.dimension(), pos) || !SpawnSpots.isStandable(level, pos)) {
                continue;
            }
            double score = -Math.sqrt(pos.distSqr(center)) / 2.0;
            if (isIndoors(level, pos)) {
                score += 30;
            }
            Optional<Double> building = buildingAt(ranked, pos);
            if (building.isPresent()) {
                score += 15 + building.get();
            }
            if (inAny(avoided, pos)) {
                score -= 40;
            }
            if (nearBed(level, pos)) {
                score += 10;
            }
            if (level.getBlockState(pos.below()).is(Blocks.DIRT_PATH)) {
                score -= 20;
            }
            if (score > bestScore) {
                bestScore = score;
                best = pos.immutable();
            }
        }
        if (best == null && level.isLoaded(center)) {
            best = surface(level, center);
        }
        return best;
    }

    private static Optional<Double> buildingAt(List<Map.Entry<Building, Double>> ranked, BlockPos pos) {
        for (Map.Entry<Building, Double> entry : ranked) {
            if (entry.getKey().box().isInside(pos)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    private static long volume(BoundingBox box) {
        return (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
    }

    private static int beds(ServerLevel level, BoundingBox box) {
        int radius = (int) Math.ceil(Math.sqrt(box.getXSpan() * box.getXSpan() + box.getZSpan() * box.getZSpan()) / 2.0) + 1;
        return (int) level.getPoiManager().getInRange(h -> h.is(PoiTypes.HOME), box.getCenter(), radius, PoiManager.Occupancy.ANY)
                .filter(record -> box.isInside(record.getPos())).limit(8).count();
    }

    /** A free floor spot beside {@code home}, preferring one against a wall, for the coffer. */
    public static Optional<BlockPos> beside(ServerLevel level, BlockPos home, int maxDistance) {
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int dx = -maxDistance; dx <= maxDistance; dx++) {
            for (int dz = -maxDistance; dz <= maxDistance; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos pos = home.offset(dx, dy, dz);
                    if (!canPlaceOn(level, pos)) {
                        continue;
                    }
                    int score = -(Math.abs(dx) + Math.abs(dz)) * 3 - Math.abs(dy) * 4;
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockState side = level.getBlockState(pos.relative(dir));
                        if (side.isCollisionShapeFullBlock(level, pos.relative(dir))) {
                            score += 4;
                        }
                        if (side.getBlock() instanceof DoorBlock) {
                            score -= 20;
                        }
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        best = pos.immutable();
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** An open-air spot near {@code near}, preferring the edge of a path, for the mailbox or the guard post. */
    public static Optional<BlockPos> outdoors(ServerLevel level, VillageRecord record, BlockPos near, int radius) {
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;
        RandomSource random = level.getRandom();
        for (int i = 0; i < 300; i++) {
            int x = near.getX() + random.nextInt(radius * 2 + 1) - radius;
            int z = near.getZ() + random.nextInt(radius * 2 + 1) - radius;
            Optional<BlockPos> spot = SpawnSpots.nearY(level, x, z, near.getY(), 6);
            if (spot.isEmpty() || !record.contains(level.dimension(), spot.get()) || !level.canSeeSky(spot.get())
                    || !canPlaceOn(level, spot.get())) {
                continue;
            }
            BlockPos pos = spot.get();
            int score = -(int) Math.sqrt(pos.distSqr(near));
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(pos.relative(dir).below()).is(Blocks.DIRT_PATH)) {
                    score += 12;
                    break;
                }
            }
            if (level.getBlockState(pos.below()).is(Blocks.DIRT_PATH)) {
                score -= 30;
            }
            if (score > bestScore) {
                bestScore = score;
                best = pos;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Whether a block could go here: replaceable space on sturdy ground, not on a path or in a doorway. */
    public static boolean canPlaceOn(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.canBeReplaced() || !state.getFluidState().isEmpty() || state.is(BlockTags.DOORS)) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState ground = level.getBlockState(below);
        return ground.isFaceSturdy(level, below, Direction.UP) && !ground.is(Blocks.DIRT_PATH) && !ground.is(BlockTags.DOORS)
                && level.getBlockState(pos.above()).canBeReplaced();
    }

    /** The ground at a column, on top of whatever is there. */
    public static BlockPos surface(ServerLevel level, BlockPos column) {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
    }

    private static boolean isIndoors(ServerLevel level, BlockPos pos) {
        if (level.canSeeSky(pos)) {
            return false;
        }
        for (int dy = 2; dy <= 6; dy++) {
            if (!level.getBlockState(pos.above(dy)).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static boolean inAny(List<BoundingBox> boxes, BlockPos pos) {
        for (BoundingBox box : boxes) {
            if (box.isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearBed(ServerLevel level, BlockPos pos) {
        return level.getPoiManager().findClosest(h -> h.is(PoiTypes.HOME), pos, 6, PoiManager.Occupancy.ANY).isPresent();
    }
}
