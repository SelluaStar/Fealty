package com.selluastar.fealty.util;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Finding safe places to put mobs: solid ground, two blocks of air, no fluids. */
public final class SpawnSpots {
    private SpawnSpots() {
    }

    /** Whether a mob could stand with its feet at this position. */
    public static boolean isStandable(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet)) {
            return false;
        }
        BlockPos below = feet.below();
        BlockState ground = level.getBlockState(below);
        if (!ground.isFaceSturdy(level, below, Direction.UP) || !ground.getFluidState().isEmpty()) {
            return false;
        }
        return isClear(level, feet) && isClear(level, feet.above());
    }

    private static boolean isClear(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    /** The standable spot in a column closest to {@code nearY}, searching up and down {@code range} blocks. */
    public static Optional<BlockPos> nearY(ServerLevel level, int x, int z, int nearY, int range) {
        for (int dy = 0; dy <= range; dy++) {
            BlockPos up = new BlockPos(x, nearY + dy, z);
            if (isStandable(level, up)) {
                return Optional.of(up);
            }
            if (dy > 0) {
                BlockPos down = new BlockPos(x, nearY - dy, z);
                if (isStandable(level, down)) {
                    return Optional.of(down);
                }
            }
        }
        return Optional.empty();
    }

    /** The top surface of a column, if it is dry and standable. */
    public static Optional<BlockPos> surface(ServerLevel level, int x, int z) {
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isLoaded(column)) {
            return Optional.empty();
        }
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        return isStandable(level, top) ? Optional.of(top) : Optional.empty();
    }
}
