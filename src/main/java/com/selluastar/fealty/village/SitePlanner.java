package com.selluastar.fealty.village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.util.SpawnSpots;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Picks spots in a village for the things Fealty adds: the elder's home, the coffer, the mailbox and the guard
 * post. It works from the structure's own pieces where it can (the largest building near the middle is taken
 * to be the hall or keep), so it copes with modded towns and castles as well as vanilla villages.
 */
public final class SitePlanner {
    private SitePlanner() {
    }

    /** Bounding boxes of the structure's buildings, largest first. Flat pieces such as roads are left out. */
    public static List<BoundingBox> buildings(ServerLevel level, VillageRecord record) {
        Optional<ChunkPos> startChunk = record.startChunk();
        if (record.structure() == null || startChunk.isEmpty()) {
            return List.of();
        }
        Structure structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(record.structure());
        if (structure == null) {
            return List.of();
        }
        ChunkAccess access = level.getChunk(startChunk.get().x, startChunk.get().z, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = access.getStartForStructure(structure);
        if (start == null || !start.isValid()) {
            return List.of();
        }
        List<BoundingBox> boxes = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            if (box.getYSpan() >= 4 && box.getXSpan() >= 4 && box.getZSpan() >= 4) {
                boxes.add(box);
            }
        }
        boxes.sort(Comparator.comparingLong((BoundingBox b) -> (long) b.getXSpan() * b.getYSpan() * b.getZSpan()).reversed());
        return boxes;
    }

    /** An indoor spot for the elder: in a large building near the middle, with a roof, near beds, off the roads. */
    @Nullable
    public static BlockPos elderHome(ServerLevel level, VillageRecord record) {
        BlockPos center = record.center();
        RandomSource random = level.getRandom();
        List<BlockPos> candidates = new ArrayList<>();
        List<BoundingBox> buildings = buildings(level, record);
        int used = 0;
        for (BoundingBox box : buildings) {
            if (used >= 6) {
                break;
            }
            if (Math.sqrt(box.getCenter().distSqr(center)) > 64) {
                continue;
            }
            used++;
            for (int i = 0; i < 80; i++) {
                candidates.add(new BlockPos(
                        box.minX() + 1 + random.nextInt(Math.max(1, box.getXSpan() - 2)),
                        box.minY() + random.nextInt(Math.max(1, box.getYSpan())),
                        box.minZ() + 1 + random.nextInt(Math.max(1, box.getZSpan() - 2))));
            }
        }
        int reach = Math.min(32, Math.max(20, Math.max(record.bounds().getXSpan(), record.bounds().getZSpan()) / 4));
        for (int i = 0; i < 400; i++) {
            candidates.add(center.offset(random.nextInt(reach * 2 + 1) - reach, random.nextInt(17) - 8, random.nextInt(reach * 2 + 1) - reach));
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
            if (inAny(buildings, pos)) {
                score += 15;
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
