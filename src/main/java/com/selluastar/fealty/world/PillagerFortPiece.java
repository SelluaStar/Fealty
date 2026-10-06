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
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A pillager fort: a plank-and-cobblestone wall with a walkway and a south gate, two corner towers, a barracks,
 * three cages of captives, a ravager pen and the War Banner in the yard.
 */
public class PillagerFortPiece extends ScatteredFeaturePiece {
    public static final int SIZE = 39;
    public static final ResourceLocation STRUCTURE = Fealty.id("pillager_fort");
    private static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, Fealty.id("chests/pillager_fort"));
    private static final int HEIGHT = 14;
    private static final int LO = 2;
    private static final int HI = SIZE - 3;
    private static final int BANNER_X = 19;
    private static final int BANNER_Z = 22;
    /** Where each cage's captives stand, from the War Banner. */
    public static final BlockPos[] CAGES = {new BlockPos(12 - BANNER_X, 0, 13 - BANNER_Z), new BlockPos(26 - BANNER_X, 0, 13 - BANNER_Z),
            new BlockPos(26 - BANNER_X, 0, 19 - BANNER_Z)};

    public PillagerFortPiece(int x, int y, int z, Direction orientation) {
        super(ModStructures.PILLAGER_FORT_PIECE.get(), x, y, z, SIZE, HEIGHT, SIZE, orientation);
        heightPosition = y;
    }

    public PillagerFortPiece(CompoundTag tag) {
        super(ModStructures.PILLAGER_FORT_PIECE.get(), tag);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        if (!updateAverageGroundHeight(level, box, 0)) {
            return;
        }
        BuildHelper b = new BuildHelper(level, box, new BlockPos(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ()));
        b.clear(1, 0, 1, SIZE - 2, HEIGHT - 1, SIZE - 2);
        b.foundation(0, 0, SIZE - 1, SIZE - 1, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        RandomSource patches = RandomSource.create(boundingBox.minX() * 31L + boundingBox.minZ());
        for (int i = 0; i < 180; i++) {
            int x = LO + 1 + patches.nextInt(HI - LO - 1);
            int z = LO + 1 + patches.nextInt(HI - LO - 1);
            b.set(x, -1, z, (i % 4 == 0 ? Blocks.GRAVEL : i % 4 == 1 ? Blocks.PODZOL : Blocks.COARSE_DIRT).defaultBlockState());
        }
        // The road from the gate to the banner, and the banner's dais.
        b.fill(18, -1, 23, 20, -1, HI, Blocks.DIRT_PATH.defaultBlockState());
        b.fill(18, -1, 21, 20, -1, 23, Blocks.COBBLESTONE.defaultBlockState());

        wall(b);
        tower(b, LO, LO, true);
        tower(b, HI - 6, LO, false);
        barracks(b, random);

        // The fire and the War Banner.
        b.set(19, 0, 17, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        BlockState logZ = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Axis.Z);
        b.fill(17, 0, 16, 17, 0, 18, logZ);
        b.fill(21, 0, 16, 21, 0, 18, logZ);
        b.set(BANNER_X, 0, BANNER_Z, ModBlocks.WAR_BANNER.get().defaultBlockState());
        BlockPos flag = b.pos(BANNER_X, 0, BANNER_Z);
        if (b.inChunk(flag) && level.getBlockEntity(flag) instanceof WarBannerBlockEntity banner) {
            banner.setNatural(STRUCTURE, CAGES);
        }

        PillagerCampPiece.cage(b, 10, 11);
        PillagerCampPiece.cage(b, 24, 11);
        PillagerCampPiece.cage(b, 24, 17);
        PillagerCampPiece.tent(b, 5, 11, Blocks.GRAY_WOOL.defaultBlockState(), Blocks.GRAY_CARPET.defaultBlockState(), random, false);
        PillagerCampPiece.tent(b, 30, 11, Blocks.WHITE_WOOL.defaultBlockState(), Blocks.LIGHT_GRAY_CARPET.defaultBlockState(), random, false);

        pen(b);

        // Stores and a smithy by the east wall.
        b.barrel(31, 0, 21, LOOT, random);
        b.barrel(32, 0, 21, LOOT, random);
        b.set(33, 0, 21, Blocks.BARREL.defaultBlockState());
        b.set(31, 1, 21, Blocks.BARREL.defaultBlockState());
        b.set(34, 0, 18, Blocks.SMITHING_TABLE.defaultBlockState());
        b.set(34, 0, 19, Blocks.ANVIL.defaultBlockState());
        b.set(34, 0, 20, Blocks.BLAST_FURNACE.defaultBlockState());
        b.set(33, 0, 18, Blocks.GRINDSTONE.defaultBlockState());

        // Archery butts west of the road.
        for (int z : new int[]{19, 22}) {
            b.set(6, 0, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
            b.set(6, 1, z, Blocks.TARGET.defaultBlockState());
            b.set(6, 2, z, Blocks.HAY_BLOCK.defaultBlockState());
        }
        b.set(11, 0, 20, Blocks.FLETCHING_TABLE.defaultBlockState());
        for (int[] post : new int[][]{{16, 24}, {22, 24}, {22, 30}, {10, 19}, {28, 23}}) {
            b.fill(post[0], 0, post[1], post[0], 1, post[1], Blocks.DARK_OAK_FENCE.defaultBlockState());
            b.set(post[0], 2, post[1], Blocks.LANTERN.defaultBlockState());
        }
    }

    /** The outer wall: cobblestone footing, planked between log posts, with a walkway and crenels. */
    private static void wall(BuildHelper b) {
        BlockState footing = Blocks.COBBLESTONE.defaultBlockState();
        BlockState planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState post = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState walk = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int i = LO; i <= HI; i++) {
            for (int[] c : new int[][]{{i, LO}, {i, HI}, {LO, i}, {HI, i}}) {
                boolean gate = c[1] == HI && c[0] >= 18 && c[0] <= 20;
                b.set(c[0], 4, c[1], (i - LO) % 4 == 0 ? post : planks);
                if (i % 2 == 0) {
                    b.set(c[0], 5, c[1], footing);
                }
                if (gate) {
                    continue;
                }
                b.set(c[0], 0, c[1], footing);
                b.fill(c[0], 1, c[1], c[0], 3, c[1], (i - LO) % 4 == 0 ? post : planks);
            }
        }
        // The walkway along the inside, a block below the top of the wall.
        for (int i = LO + 1; i <= HI - 1; i++) {
            b.set(i, 3, LO + 1, walk);
            b.set(i, 3, HI - 1, walk);
            b.set(LO + 1, 3, i, walk);
            b.set(HI - 1, 3, i, walk);
        }
        BlockState ladderEast = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        BlockState ladderWest = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
        b.fill(LO + 1, 0, 26, LO + 1, 3, 26, ladderEast);
        b.fill(HI - 1, 0, 26, HI - 1, 3, 26, ladderWest);
        // Gate towers either side of the road.
        for (int x : new int[]{17, 21}) {
            b.fill(x, 0, HI, x, 6, HI + 1, footing);
            b.set(x, 7, HI, Blocks.LANTERN.defaultBlockState());
        }
        b.fill(18, 5, HI, 20, 5, HI, footing);
        b.fill(18, 6, HI, 20, 6, HI, Blocks.COBBLESTONE_WALL.defaultBlockState());
        b.set(19, 6, HI, Blocks.DARK_OAK_FENCE.defaultBlockState());
    }

    /**
     * A 7x7 cobblestone tower in a corner, its floor level with the walkway and open onto it, a roof with crenels
     * and a ladder up through both.
     */
    private static void tower(BuildHelper b, int x, int z, boolean west) {
        BlockState stone = Blocks.COBBLESTONE.defaultBlockState();
        BlockState mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        int x2 = x + 6;
        int z2 = z + 6;
        b.walls(x, 0, z, x2, 9, z2, stone);
        b.fill(x, 0, z, x, 9, z, mossy);
        b.fill(x2, 0, z2, x2, 2, z2, mossy);
        b.clear(x + 1, 0, z + 1, x2 - 1, 9, z2 - 1);
        b.fill(x + 1, 3, z + 1, x2 - 1, 3, z2 - 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
        b.fill(x + 1, 9, z + 1, x2 - 1, 9, z2 - 1, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        for (int i = 0; i <= 6; i += 2) {
            b.set(x + i, 10, z, stone);
            b.set(x + i, 10, z2, stone);
            b.set(x, 10, z + i, stone);
            b.set(x2, 10, z + i, stone);
        }
        // A door at the foot, and openings onto the walkway on the yard sides.
        b.door(x + 3, 0, z2, Blocks.DARK_OAK_DOOR, Direction.SOUTH);
        int innerX = west ? x2 : x;
        int walkX = west ? x + 1 : x2 - 1;
        b.fill(innerX, 4, z + 1, innerX, 5, z + 1, Blocks.AIR.defaultBlockState());
        b.fill(walkX, 4, z2, walkX, 5, z2, Blocks.AIR.defaultBlockState());
        // Arrow slits on the outer faces.
        int outerX = west ? x : x2;
        b.fill(outerX, 5, z + 2, outerX, 6, z + 2, Blocks.AIR.defaultBlockState());
        b.fill(x + 3, 5, z, x + 3, 6, z, Blocks.AIR.defaultBlockState());
        int ladderX = west ? x + 1 : x2 - 1;
        b.fill(ladderX, 0, z + 3, ladderX, 9, z + 3, Blocks.LADDER.defaultBlockState()
                .setValue(LadderBlock.FACING, west ? Direction.EAST : Direction.WEST));
        b.set(x + 3, 4, z + 3, Blocks.LANTERN.defaultBlockState());
        b.set(x + 3, 10, z + 3, Blocks.LANTERN.defaultBlockState());
        b.set(x + 4, 0, z + 1, Blocks.BARREL.defaultBlockState());
    }

    /** A long plank hall with bunks, west of the road. */
    private static void barracks(BuildHelper b, RandomSource random) {
        int x1 = 6;
        int z1 = 26;
        int x2 = 16;
        int z2 = 33;
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        b.fill(x1, -1, z1, x2, -1, z2, Blocks.COBBLESTONE.defaultBlockState());
        b.fill(x1 + 1, -1, z1 + 1, x2 - 1, -1, z2 - 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
        b.walls(x1, 0, z1, x2, 3, z2, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}, {11, z1}, {11, z2}}) {
            b.fill(c[0], 0, c[1], c[0], 3, c[1], log);
        }
        b.fill(x1, 4, z1, x2, 4, z2, Blocks.DARK_OAK_SLAB.defaultBlockState());
        b.fill(x1 + 1, 5, z1 + 3, x2 - 1, 5, z2 - 3, Blocks.DARK_OAK_SLAB.defaultBlockState());
        b.door(13, 0, z1, Blocks.DARK_OAK_DOOR, Direction.NORTH);
        for (int x : new int[]{8, 15}) {
            b.set(x, 1, z1, Blocks.GLASS_PANE.defaultBlockState());
            b.set(x, 1, z2, Blocks.GLASS_PANE.defaultBlockState());
        }
        for (int x : new int[]{7, 9, 13, 15}) {
            b.bed(x, 0, z2 - 2, x % 4 == 1 ? Blocks.GRAY_BED : Blocks.WHITE_BED, Direction.SOUTH);
        }
        b.chest(11, 0, z2 - 1, Direction.NORTH, LOOT, random);
        b.set(7, 0, z1 + 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        b.set(8, 0, z1 + 1, Blocks.BARREL.defaultBlockState());
        b.set(10, 3, z1 + 3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    /** A fenced pen for war beasts in the south-east corner. */
    private static void pen(BuildHelper b) {
        BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
        b.walls(27, 0, 26, 34, 1, 33, fence);
        b.fill(27, 0, 29, 27, 1, 30, Blocks.AIR.defaultBlockState());
        b.set(27, 0, 29, Blocks.DARK_OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST));
        b.set(27, 0, 30, Blocks.DARK_OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST));
        b.fill(28, -1, 27, 33, -1, 32, Blocks.COARSE_DIRT.defaultBlockState());
        b.set(32, 0, 31, Blocks.HAY_BLOCK.defaultBlockState());
        b.set(33, 0, 31, Blocks.HAY_BLOCK.defaultBlockState());
        b.set(33, 1, 31, Blocks.HAY_BLOCK.defaultBlockState());
        b.set(33, 0, 27, Blocks.WATER_CAULDRON.defaultBlockState()
                .setValue(LayeredCauldronBlock.LEVEL, 3));
    }
}
