package com.selluastar.fealty.world;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.block.WarBannerBlockEntity;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.registry.ModStructures;
import com.selluastar.fealty.war.Strongholds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Builds a pillager camp: a dark-oak palisade with a gate, tents, a watchtower, two iron-bar cages for captives and
 * the camp's War Banner by the fire. The banner mans the camp and fills the cages when first loaded.
 */
public class PillagerCampPiece extends ScatteredFeaturePiece {
    public static final int SIZE = 29;
    private static final ResourceKey<LootTable> CAMP_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Fealty.id("chests/pillager_camp"));
    private static final int BANNER_X = 14;
    private static final int BANNER_Z = 16;
    /** Where each cage's captives stand, from the War Banner. */
    public static final BlockPos[] CAGES = {new BlockPos(17 - BANNER_X, 0, 5 - BANNER_Z), new BlockPos(23 - BANNER_X, 0, 22 - BANNER_Z)};

    public PillagerCampPiece(int x, int y, int z, Direction orientation) {
        super(ModStructures.PILLAGER_CAMP_PIECE.get(), x, y, z, SIZE, 14, SIZE, orientation);
        heightPosition = y;
    }

    public PillagerCampPiece(CompoundTag tag) {
        super(ModStructures.PILLAGER_CAMP_PIECE.get(), tag);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        if (!updateAverageGroundHeight(level, box, 0)) {
            return;
        }
        BuildHelper b = new BuildHelper(level, box, new BlockPos(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ()));
        b.clear(1, 0, 1, SIZE - 2, 13, SIZE - 2);
        b.foundation(0, 0, SIZE - 1, SIZE - 1, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        RandomSource patches = RandomSource.create(boundingBox.minX() * 31L + boundingBox.minZ());
        for (int i = 0; i < 110; i++) {
            int x = 2 + patches.nextInt(SIZE - 4);
            int z = 2 + patches.nextInt(SIZE - 4);
            b.set(x, -1, z, (i % 4 == 0 ? Blocks.DIRT_PATH : i % 4 == 1 ? Blocks.PODZOL : Blocks.COARSE_DIRT).defaultBlockState());
        }
        // The trodden way from the gate to the fire.
        b.fill(13, -1, 17, 15, -1, 26, Blocks.DIRT_PATH.defaultBlockState());

        palisade(b);

        // The fire, its seats and the War Banner.
        b.fill(13, -1, 11, 15, -1, 13, Blocks.COARSE_DIRT.defaultBlockState());
        b.set(14, 0, 12, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        BlockState logX = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Axis.X);
        BlockState logZ = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Axis.Z);
        b.fill(13, 0, 9, 15, 0, 9, logX);
        b.fill(11, 0, 11, 11, 0, 13, logZ);
        b.fill(17, 0, 11, 17, 0, 13, logZ);
        b.set(BANNER_X, 0, BANNER_Z, ModBlocks.WAR_BANNER.get().defaultBlockState());
        BlockPos flag = b.pos(BANNER_X, 0, BANNER_Z);
        if (b.inChunk(flag) && level.getBlockEntity(flag) instanceof WarBannerBlockEntity banner) {
            banner.setNatural(Strongholds.PILLAGER_CAMP, CAGES);
        }

        tent(b, 3, 3, Blocks.GRAY_WOOL.defaultBlockState(), Blocks.GRAY_CARPET.defaultBlockState(), random, true);
        tent(b, 9, 3, Blocks.WHITE_WOOL.defaultBlockState(), Blocks.LIGHT_GRAY_CARPET.defaultBlockState(), random, false);
        tent(b, 3, 20, Blocks.GRAY_WOOL.defaultBlockState(), Blocks.BLACK_CARPET.defaultBlockState(), random, true);
        cage(b, 15, 3);
        cage(b, 21, 20);
        tower(b, 21, 3);

        // Stores, a forge corner and a practice dummy.
        b.barrel(19, 0, 15, CAMP_LOOT, random);
        b.barrel(20, 0, 15, CAMP_LOOT, random);
        b.set(21, 0, 15, Blocks.FLETCHING_TABLE.defaultBlockState());
        b.set(18, 0, 15, Blocks.CRAFTING_TABLE.defaultBlockState());
        b.fill(3, 0, 13, 6, 0, 13, logX);
        b.fill(3, 1, 13, 5, 1, 13, logX);
        b.set(9, 0, 22, Blocks.DARK_OAK_FENCE.defaultBlockState());
        b.set(9, 1, 22, Blocks.HAY_BLOCK.defaultBlockState());
        b.set(9, 2, 22, Blocks.CARVED_PUMPKIN.defaultBlockState().setValue(CarvedPumpkinBlock.FACING, Direction.SOUTH));
        for (int[] post : new int[][]{{8, 10}, {20, 10}, {8, 17}, {19, 18}}) {
            b.fill(post[0], 0, post[1], post[0], 1, post[1], Blocks.DARK_OAK_FENCE.defaultBlockState());
            b.set(post[0], 2, post[1], Blocks.LANTERN.defaultBlockState());
        }
    }

    /** A ring of sharpened dark-oak logs, with a gate in the south wall. */
    private static void palisade(BuildHelper b) {
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState point = Blocks.DARK_OAK_FENCE.defaultBlockState();
        int lo = 1;
        int hi = SIZE - 2;
        for (int i = lo; i <= hi; i++) {
            for (int[] c : new int[][]{{i, lo}, {i, hi}, {lo, i}, {hi, i}}) {
                boolean gate = c[1] == hi && c[0] >= 13 && c[0] <= 15;
                if (gate) {
                    continue;
                }
                b.fill(c[0], 0, c[1], c[0], 2, c[1], log);
                if (i % 2 == 0) {
                    b.set(c[0], 3, c[1], point);
                }
            }
        }
        // Gateposts with torches.
        b.fill(12, 3, hi, 12, 3, hi, log);
        b.fill(16, 3, hi, 16, 3, hi, log);
        b.set(12, 4, hi, Blocks.LANTERN.defaultBlockState());
        b.set(16, 4, hi, Blocks.LANTERN.defaultBlockState());
    }

    /** An A-frame tent five blocks long, open at the south end. */
    static void tent(BuildHelper b, int x, int z, BlockState wool, BlockState carpet, RandomSource random, boolean chest) {
        for (int dz = 0; dz < 5; dz++) {
            b.set(x, 0, z + dz, wool);
            b.set(x + 4, 0, z + dz, wool);
            b.set(x + 1, 1, z + dz, wool);
            b.set(x + 3, 1, z + dz, wool);
            b.set(x + 2, 2, z + dz, wool);
        }
        b.fill(x + 1, 0, z, x + 3, 0, z, wool);
        b.set(x + 2, 1, z, wool);
        b.fill(x + 1, 0, z + 1, x + 3, 0, z + 3, carpet);
        if (chest) {
            b.chest(x + 2, 0, z + 1, Direction.SOUTH, CAMP_LOOT, random);
        }
        b.fill(x + 2, 0, z + 5, x + 2, 1, z + 5, Blocks.DARK_OAK_FENCE.defaultBlockState());
    }

    /** A 5x5 iron-bar cage with a plank roof; captives stand in the middle (see {@link #CAGES}). */
    static void cage(BuildHelper b, int x, int z) {
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        BlockState post = Blocks.DARK_OAK_LOG.defaultBlockState();
        b.fill(x, -1, z, x + 4, -1, z + 4, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int y = 0; y <= 2; y++) {
            b.fill(x, y, z, x + 4, y, z, bars);
            b.fill(x, y, z + 4, x + 4, y, z + 4, bars);
            b.fill(x, y, z, x, y, z + 4, bars);
            b.fill(x + 4, y, z, x + 4, y, z + 4, bars);
        }
        for (int[] c : new int[][]{{x, z}, {x + 4, z}, {x, z + 4}, {x + 4, z + 4}}) {
            b.fill(c[0], 0, c[1], c[0], 3, c[1], post);
        }
        b.fill(x, 3, z, x + 4, 3, z + 4, Blocks.DARK_OAK_SLAB.defaultBlockState());
        b.set(x + 1, 0, z + 1, Blocks.HAY_BLOCK.defaultBlockState());
    }

    /** A lookout on four posts, with a ladder and a rail. */
    private static void tower(BuildHelper b, int x, int z) {
        BlockState post = Blocks.DARK_OAK_LOG.defaultBlockState();
        for (int[] c : new int[][]{{x, z}, {x + 4, z}, {x, z + 4}, {x + 4, z + 4}}) {
            b.fill(c[0], 0, c[1], c[0], 7, c[1], post);
        }
        b.fill(x + 2, 0, z + 4, x + 2, 6, z + 4, post);
        b.fill(x + 2, 0, z + 5, x + 2, 7, z + 5, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        b.fill(x, 7, z, x + 4, 7, z + 4, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        b.walls(x, 8, z, x + 4, 8, z + 4, Blocks.DARK_OAK_FENCE.defaultBlockState());
        b.set(x + 2, 8, z + 4, Blocks.AIR.defaultBlockState());
        b.set(x + 2, 8, z + 2, Blocks.LANTERN.defaultBlockState());
    }
}
