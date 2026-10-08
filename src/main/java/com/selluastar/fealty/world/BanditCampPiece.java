package com.selluastar.fealty.world;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.block.BanditStandardBlockEntity;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.core.Direction.Axis;

/** Builds a bandit camp around its standard. The standard spawns the camp's people when first loaded. */
public class BanditCampPiece extends ScatteredFeaturePiece {
    public static final int SIZE = 25;
    private static final ResourceKey<LootTable> CAMP_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Fealty.id("chests/bandit_camp"));

    public BanditCampPiece(int x, int y, int z, Direction orientation) {
        super(ModStructures.BANDIT_CAMP_PIECE.get(), x, y, z, SIZE, 10, SIZE, orientation);
        heightPosition = y;
    }

    public BanditCampPiece(CompoundTag tag) {
        super(ModStructures.BANDIT_CAMP_PIECE.get(), tag);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        if (!updateAverageGroundHeight(level, box, 0)) {
            return;
        }
        BuildHelper b = new BuildHelper(level, box, new BlockPos(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ()));
        b.clear(2, 0, 2, SIZE - 3, 14, SIZE - 3);
        b.foundation(1, 1, SIZE - 2, SIZE - 2, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        RandomSource patches = RandomSource.create(boundingBox.minX() * 31L + boundingBox.minZ());
        for (int i = 0; i < 70; i++) {
            int x = 3 + patches.nextInt(SIZE - 6);
            int z = 3 + patches.nextInt(SIZE - 6);
            b.set(x, -1, z, (i % 3 == 0 ? Blocks.PODZOL : Blocks.COARSE_DIRT).defaultBlockState());
        }

        // Fire pit and seats
        b.fill(11, -1, 11, 13, -1, 13, Blocks.COARSE_DIRT.defaultBlockState());
        b.set(12, 0, 12, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        BlockState logX = Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Axis.X);
        BlockState logZ = Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Axis.Z);
        b.fill(11, 0, 9, 13, 0, 9, logX);
        b.fill(11, 0, 15, 13, 0, 15, logX);
        b.fill(9, 0, 11, 9, 0, 13, logZ);
        b.set(14, 0, 14, ModBlocks.BANDIT_STANDARD.get().defaultBlockState());
        BlockPos flag = b.pos(14, 0, 14);
        if (b.inChunk(flag) && level.getBlockEntity(flag) instanceof BanditStandardBlockEntity standard) {
            standard.setNatural();
        }

        tent(b, 3, 3, Blocks.BROWN_WOOL.defaultBlockState(), Blocks.BROWN_CARPET.defaultBlockState(), random, true);
        tent(b, 16, 3, Blocks.BLACK_WOOL.defaultBlockState(), Blocks.GRAY_CARPET.defaultBlockState(), random, false);
        tent(b, 3, 16, Blocks.RED_WOOL.defaultBlockState(), Blocks.RED_CARPET.defaultBlockState(), random, true);
        lookout(b);

        // Stores and clutter
        b.barrel(9, 0, 16, CAMP_LOOT, random);
        b.barrel(16, 0, 9, CAMP_LOOT, random);
        b.set(16, 0, 10, Blocks.HAY_BLOCK.defaultBlockState());
        b.set(15, 0, 16, Blocks.CRAFTING_TABLE.defaultBlockState());
        b.set(10, 0, 16, Blocks.SMITHING_TABLE.defaultBlockState());
        for (int[] post : new int[][]{{8, 8}, {16, 16}, {8, 17}}) {
            b.fill(post[0], 0, post[1], post[0], 1, post[1], Blocks.SPRUCE_FENCE.defaultBlockState());
            b.set(post[0], 2, post[1], Blocks.LANTERN.defaultBlockState());
        }
    }

    /** An A-frame tent five blocks long, open at the front (south end). */
    private static void tent(BuildHelper b, int x, int z, BlockState wool, BlockState carpet, RandomSource random, boolean chest) {
        for (int dz = 0; dz < 5; dz++) {
            b.set(x, 0, z + dz, wool);
            b.set(x + 4, 0, z + dz, wool);
            b.set(x + 1, 1, z + dz, wool);
            b.set(x + 3, 1, z + dz, wool);
            b.set(x + 2, 2, z + dz, wool);
        }
        b.fill(x + 1, 0, z, x + 3, 0, z, wool);
        b.fill(x + 2, 1, z, x + 2, 1, z, wool);
        b.fill(x + 1, 0, z + 1, x + 3, 0, z + 3, carpet);
        if (chest) {
            b.chest(x + 2, 0, z + 1, Direction.SOUTH, CAMP_LOOT, random);
        }
        b.fill(x + 2, 0, z + 5, x + 2, 1, z + 5, Blocks.OAK_FENCE.defaultBlockState());
    }

    private static void lookout(BuildHelper b) {
        BlockState post = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int[] c : new int[][]{{18, 18}, {20, 18}, {18, 20}, {20, 20}}) {
            b.fill(c[0], 0, c[1], c[0], 5, c[1], post);
        }
        b.fill(19, 0, 18, 19, 5, 18, post);
        b.fill(19, 0, 17, 19, 5, 17, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
        b.fill(17, 6, 17, 21, 6, 21, Blocks.SPRUCE_PLANKS.defaultBlockState());
        b.set(19, 6, 17, Blocks.AIR.defaultBlockState());
        b.walls(17, 7, 17, 21, 7, 21, Blocks.SPRUCE_FENCE.defaultBlockState());
        b.set(19, 7, 17, Blocks.AIR.defaultBlockState());
        b.set(19, 7, 19, Blocks.LANTERN.defaultBlockState());
        b.set(21, 8, 21, Blocks.RED_BANNER.defaultBlockState());
    }
}
