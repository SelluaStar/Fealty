package com.selluastar.fealty.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Places blocks for Fealty's code-built structures in absolute, unrotated coordinates relative to an origin,
 * only inside the chunk currently being generated.
 */
public final class BuildHelper {
    private final WorldGenLevel level;
    private final BoundingBox chunkBox;
    private final BlockPos origin;

    public BuildHelper(WorldGenLevel level, BoundingBox chunkBox, BlockPos origin) {
        this.level = level;
        this.chunkBox = chunkBox;
        this.origin = origin;
    }

    public BlockPos pos(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    public boolean inChunk(BlockPos pos) {
        return chunkBox.isInside(pos);
    }

    public void set(int x, int y, int z, BlockState state) {
        BlockPos pos = pos(x, y, z);
        if (chunkBox.isInside(pos)) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    /** Turn everything in the box to air (skips blocks that already are). */
    public void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                    BlockPos pos = pos(x, y, z);
                    if (chunkBox.isInside(pos) && !level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** Walls of a box (no top or bottom). */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        fill(x1, y1, z1, x2, y2, z1, state);
        fill(x1, y1, z2, x2, y2, z2, state);
        fill(x1, y1, z1, x1, y2, z2, state);
        fill(x2, y1, z1, x2, y2, z2, state);
    }

    /** Solid ground under a footprint, filling down to the terrain so nothing floats. */
    public void foundation(int x1, int z1, int x2, int z2, BlockState top, BlockState fill) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                set(x, -1, z, top);
                for (int y = -2; y > -12; y--) {
                    BlockPos pos = pos(x, y, z);
                    if (!chunkBox.isInside(pos)) {
                        break;
                    }
                    BlockState existing = level.getBlockState(pos);
                    if (!existing.isAir() && existing.getFluidState().isEmpty() && !existing.canBeReplaced()) {
                        break;
                    }
                    level.setBlock(pos, fill, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    public void door(int x, int y, int z, Block door, Direction facing) {
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        set(x, y, z, lower);
        set(x, y + 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A bed with its foot at (x, z), head one block towards {@code facing}. */
    public void bed(int x, int y, int z, Block bed, Direction facing) {
        BlockState foot = bed.defaultBlockState().setValue(BedBlock.FACING, facing).setValue(BedBlock.PART, BedPart.FOOT);
        set(x, y, z, foot);
        set(x + facing.getStepX(), y, z + facing.getStepZ(), foot.setValue(BedBlock.PART, BedPart.HEAD));
    }

    public void chest(int x, int y, int z, Direction facing, ResourceKey<LootTable> loot, RandomSource random) {
        BlockPos pos = pos(x, y, z);
        if (chunkBox.isInside(pos)) {
            level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, facing),
                    Block.UPDATE_CLIENTS);
            RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot);
        }
    }

    public void barrel(int x, int y, int z, ResourceKey<LootTable> loot, RandomSource random) {
        BlockPos pos = pos(x, y, z);
        if (chunkBox.isInside(pos)) {
            level.setBlock(pos, Blocks.BARREL.defaultBlockState(), Block.UPDATE_CLIENTS);
            RandomizableContainer.setBlockEntityLootTable(level, random, pos, loot);
        }
    }

    public WorldGenLevel level() {
        return level;
    }
}
