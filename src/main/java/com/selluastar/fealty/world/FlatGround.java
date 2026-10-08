package com.selluastar.fealty.world;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

/** Finds a dry, reasonably flat spot for a surface structure centred on a chunk. */
final class FlatGround {
    private FlatGround() {
    }

    /** @return the north-west corner of a {@code size}-wide footprint at ground level, or empty if unsuitable */
    static Optional<BlockPos> find(Structure.GenerationContext context, int size, int maxSlope) {
        ChunkPos chunk = context.chunkPos();
        int x0 = chunk.getMiddleBlockX() - size / 2;
        int z0 = chunk.getMiddleBlockZ() - size / 2;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int[][] samples = {{0, 0}, {size - 1, 0}, {0, size - 1}, {size - 1, size - 1}, {size / 2, size / 2}};
        for (int[] s : samples) {
            int x = x0 + s[0];
            int z = z0 + s[1];
            int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());
            NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, context.heightAccessor(), context.randomState());
            BlockState top = column.getBlock(y - 1);
            if (!top.getFluidState().isEmpty()) {
                return Optional.empty();
            }
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        if (max - min > maxSlope) {
            return Optional.empty();
        }
        return Optional.of(new BlockPos(x0, min, z0));
    }
}
