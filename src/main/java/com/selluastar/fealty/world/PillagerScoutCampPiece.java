package com.selluastar.fealty.world;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.block.WarBannerBlockEntity;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.registry.ModStructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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

/** A pillager scout camp: two tents, a fire, a lookout and one cage, around a War Banner. */
public class PillagerScoutCampPiece extends ScatteredFeaturePiece {
    public static final int SIZE = 15;
    public static final ResourceLocation STRUCTURE = Fealty.id("pillager_scout_camp");
    private static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, Fealty.id("chests/pillager_scout_camp"));
    private static final int BANNER_X = 7;
    private static final int BANNER_Z = 8;
    public static final BlockPos[] CAGES = {new BlockPos(11 - BANNER_X, 0, 11 - BANNER_Z)};

    public PillagerScoutCampPiece(int x, int y, int z, Direction orientation) {
        super(ModStructures.PILLAGER_SCOUT_CAMP_PIECE.get(), x, y, z, SIZE, 9, SIZE, orientation);
        heightPosition = y;
    }

    public PillagerScoutCampPiece(CompoundTag tag) {
        super(ModStructures.PILLAGER_SCOUT_CAMP_PIECE.get(), tag);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        if (!updateAverageGroundHeight(level, box, 0)) {
            return;
        }
        BuildHelper b = new BuildHelper(level, box, new BlockPos(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ()));
        b.clear(1, 0, 1, SIZE - 2, 8, SIZE - 2);
        b.foundation(0, 0, SIZE - 1, SIZE - 1, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        RandomSource patches = RandomSource.create(boundingBox.minX() * 31L + boundingBox.minZ());
        for (int i = 0; i < 30; i++) {
            b.set(1 + patches.nextInt(SIZE - 2), -1, 1 + patches.nextInt(SIZE - 2),
                    (i % 3 == 0 ? Blocks.PODZOL : Blocks.COARSE_DIRT).defaultBlockState());
        }
        b.set(7, 0, 6, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        BlockState logZ = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Axis.Z);
        b.set(5, 0, 6, logZ);
        b.set(9, 0, 6, logZ);
        b.set(BANNER_X, 0, BANNER_Z, ModBlocks.WAR_BANNER.get().defaultBlockState());
        BlockPos flag = b.pos(BANNER_X, 0, BANNER_Z);
        if (b.inChunk(flag) && level.getBlockEntity(flag) instanceof WarBannerBlockEntity banner) {
            banner.setNatural(STRUCTURE, CAGES);
        }
        PillagerCampPiece.tent(b, 1, 1, Blocks.GRAY_WOOL.defaultBlockState(), Blocks.GRAY_CARPET.defaultBlockState(), random, false);
        PillagerCampPiece.tent(b, 9, 1, Blocks.WHITE_WOOL.defaultBlockState(), Blocks.LIGHT_GRAY_CARPET.defaultBlockState(), random, false);
        PillagerCampPiece.cage(b, 9, 9);
        // A lookout on three posts, with a ladder.
        BlockState post = Blocks.DARK_OAK_LOG.defaultBlockState();
        for (int[] c : new int[][]{{2, 10}, {4, 10}, {2, 12}, {4, 12}, {3, 12}}) {
            b.fill(c[0], 0, c[1], c[0], 2, c[1], post);
        }
        b.fill(2, 3, 10, 4, 3, 12, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        b.fill(3, 0, 13, 3, 3, 13, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
        b.set(2, 4, 10, Blocks.DARK_OAK_FENCE.defaultBlockState());
        b.set(4, 4, 10, Blocks.DARK_OAK_FENCE.defaultBlockState());
        b.set(3, 4, 11, Blocks.LANTERN.defaultBlockState());
        b.barrel(6, 0, 12, LOOT, random);
        b.set(7, 0, 12, Blocks.HAY_BLOCK.defaultBlockState());
        b.fill(12, 0, 7, 12, 1, 7, Blocks.DARK_OAK_FENCE.defaultBlockState());
        b.set(12, 2, 7, Blocks.LANTERN.defaultBlockState());
    }
}
