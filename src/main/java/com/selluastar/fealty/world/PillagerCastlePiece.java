package com.selluastar.fealty.world;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.block.WarBannerBlockEntity;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.registry.ModStructures;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * A pillager castle: stone-brick curtain walls with a walkway, four corner towers and a gatehouse with a raised
 * portcullis; a three-storey keep with dungeon cells for captives; and a courtyard of tents, a forge and a beast
 * pen around the War Banner.
 */
public class PillagerCastlePiece extends ScatteredFeaturePiece {
    public static final int SIZE = 51;
    public static final ResourceLocation STRUCTURE = Fealty.id("pillager_castle");
    private static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE, Fealty.id("chests/pillager_castle"));
    private static final ResourceKey<LootTable> STORES = ResourceKey.create(Registries.LOOT_TABLE, Fealty.id("chests/pillager_fort"));
    private static final int HEIGHT = 20;
    private static final int LO = 2;
    private static final int HI = SIZE - 3;
    private static final int WALL_TOP = 6;
    private static final int GATE_X = 25;
    private static final int BANNER_X = 25;
    private static final int BANNER_Z = 32;
    /** The keep: its walls, and the floors above the ground storey. */
    private static final int KEEP_X1 = 18;
    private static final int KEEP_Z1 = 10;
    private static final int KEEP_X2 = 32;
    private static final int KEEP_Z2 = 24;
    private static final int KEEP_TOP = 15;
    /** Where each dungeon cell's captives stand, from the War Banner. */
    public static final BlockPos[] CAGES = {new BlockPos(21 - BANNER_X, 0, 12 - BANNER_Z), new BlockPos(25 - BANNER_X, 0, 12 - BANNER_Z),
            new BlockPos(29 - BANNER_X, 0, 12 - BANNER_Z)};

    public PillagerCastlePiece(int x, int y, int z, Direction orientation) {
        super(ModStructures.PILLAGER_CASTLE_PIECE.get(), x, y, z, SIZE, HEIGHT, SIZE, orientation);
        heightPosition = y;
    }

    public PillagerCastlePiece(CompoundTag tag) {
        super(ModStructures.PILLAGER_CASTLE_PIECE.get(), tag);
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
        for (int i = 0; i < 320; i++) {
            int x = LO + 1 + patches.nextInt(HI - LO - 1);
            int z = LO + 1 + patches.nextInt(HI - LO - 1);
            b.set(x, -1, z, (i % 5 == 0 ? Blocks.COBBLESTONE : i % 5 == 1 ? Blocks.GRAVEL : i % 5 == 2 ? Blocks.PODZOL
                    : Blocks.COARSE_DIRT).defaultBlockState());
        }
        // The road from the gate to the keep, and the banner's dais.
        b.fill(GATE_X - 1, -1, KEEP_Z2 + 1, GATE_X + 1, -1, HI, Blocks.GRAVEL.defaultBlockState());
        b.fill(BANNER_X - 2, -1, BANNER_Z - 2, BANNER_X + 2, -1, BANNER_Z + 2, Blocks.POLISHED_ANDESITE.defaultBlockState());
        b.fill(BANNER_X - 1, -1, BANNER_Z - 1, BANNER_X + 1, -1, BANNER_Z + 1, Blocks.STONE_BRICKS.defaultBlockState());

        curtainWall(b, patches);
        tower(b, LO, LO, true, true);
        tower(b, HI - 8, LO, false, true);
        tower(b, LO, HI - 8, true, false);
        tower(b, HI - 8, HI - 8, false, false);
        gatehouse(b);
        keep(b, patches, random);

        b.set(BANNER_X, 0, BANNER_Z, ModBlocks.WAR_BANNER.get().defaultBlockState());
        BlockPos flag = b.pos(BANNER_X, 0, BANNER_Z);
        if (b.inChunk(flag) && level.getBlockEntity(flag) instanceof WarBannerBlockEntity banner) {
            banner.setNatural(STRUCTURE, CAGES);
        }
        for (int x : new int[]{BANNER_X - 3, BANNER_X + 3}) {
            b.set(x, 0, BANNER_Z, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        }

        // The courtyard: tents, a forge, a well, butts, stores and a beast pen.
        PillagerCampPiece.tent(b, 5, 13, Blocks.GRAY_WOOL.defaultBlockState(), Blocks.BLACK_CARPET.defaultBlockState(), random, false);
        PillagerCampPiece.tent(b, 11, 13, Blocks.WHITE_WOOL.defaultBlockState(), Blocks.GRAY_CARPET.defaultBlockState(), random, false);
        PillagerCampPiece.tent(b, 36, 11, Blocks.GRAY_WOOL.defaultBlockState(), Blocks.GRAY_CARPET.defaultBlockState(), random, false);
        forge(b, random);
        well(b, 11, 28);
        for (int z : new int[]{30, 33, 36}) {
            b.set(5, 0, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
            b.set(5, 1, z, Blocks.TARGET.defaultBlockState());
            b.set(5, 2, z, Blocks.HAY_BLOCK.defaultBlockState());
        }
        b.set(9, 0, 33, Blocks.FLETCHING_TABLE.defaultBlockState());
        pen(b);
        for (int[] post : new int[][]{{21, 27}, {29, 27}, {21, 40}, {29, 40}, {9, 22}, {41, 22}, {15, 36}, {35, 38}}) {
            b.fill(post[0], 0, post[1], post[0], 1, post[1], Blocks.DARK_OAK_FENCE.defaultBlockState());
            b.set(post[0], 2, post[1], Blocks.LANTERN.defaultBlockState());
        }
    }

    /** Stone-brick walls round the castle with a walkway inside and crenels above; the gate is in the south wall. */
    private static void curtainWall(BuildHelper b, RandomSource patches) {
        BlockState walk = Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        for (int i = LO; i <= HI; i++) {
            for (int[] c : new int[][]{{i, LO}, {i, HI}, {LO, i}, {HI, i}}) {
                boolean gate = c[1] == HI && Math.abs(c[0] - GATE_X) <= 1;
                for (int y = gate ? 5 : 0; y <= WALL_TOP; y++) {
                    b.set(c[0], y, c[1], brick(patches));
                }
                if (i % 2 == 0) {
                    b.set(c[0], WALL_TOP + 1, c[1], Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
        }
        for (int i = LO + 1; i <= HI - 1; i++) {
            b.set(i, 5, LO + 1, walk);
            b.set(i, 5, HI - 1, walk);
            b.set(LO + 1, 5, i, walk);
            b.set(HI - 1, 5, i, walk);
        }
        BlockState ladder = Blocks.LADDER.defaultBlockState();
        b.fill(LO + 1, 0, 26, LO + 1, 5, 26, ladder.setValue(LadderBlock.FACING, Direction.EAST));
        b.fill(HI - 1, 0, 26, HI - 1, 5, 26, ladder.setValue(LadderBlock.FACING, Direction.WEST));
        b.fill(19, 0, HI - 1, 19, 5, HI - 1, ladder.setValue(LadderBlock.FACING, Direction.NORTH));
        b.fill(31, 0, HI - 1, 31, 5, HI - 1, ladder.setValue(LadderBlock.FACING, Direction.NORTH));
    }

    /** Two squat towers either side of the gate, outside the wall, with the portcullis raised between them. */
    private static void gatehouse(BuildHelper b) {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x : new int[]{GATE_X - 4, GATE_X + 2}) {
            b.fill(x, 0, HI, x + 2, 9, HI + 2, stone);
            b.set(x, 10, HI + 2, stone);
            b.set(x + 2, 10, HI + 2, stone);
            b.set(x, 10, HI, stone);
            b.set(x + 2, 10, HI, stone);
            b.set(x + 1, 10, HI + 1, Blocks.LANTERN.defaultBlockState());
        }
        b.fill(GATE_X - 1, 4, HI + 1, GATE_X + 1, 4, HI + 1, Blocks.IRON_BARS.defaultBlockState());
        b.fill(GATE_X - 1, 5, HI + 1, GATE_X + 1, 9, HI + 2, stone);
        b.fill(GATE_X - 1, 10, HI + 2, GATE_X + 1, 10, HI + 2, Blocks.STONE_BRICK_WALL.defaultBlockState());
        b.fill(GATE_X - 1, -1, HI, GATE_X + 1, -1, SIZE - 1, Blocks.GRAVEL.defaultBlockState());
        b.set(GATE_X - 2, 3, HI - 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        b.set(GATE_X + 2, 3, HI - 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        b.fill(GATE_X - 2, 4, HI - 1, GATE_X - 2, 4, HI - 1, stone);
        b.fill(GATE_X + 2, 4, HI - 1, GATE_X + 2, 4, HI - 1, stone);
    }

    /**
     * A 9x9 corner tower: a ground room with a door to the courtyard, an upper floor level with the wall walk and
     * open onto it, a crenellated roof and a ladder through all three.
     */
    private static void tower(BuildHelper b, int x, int z, boolean west, boolean north) {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        int x2 = x + 8;
        int z2 = z + 8;
        b.walls(x, 0, z, x2, 11, z2, stone);
        b.fill(west ? x : x2, 0, north ? z : z2, west ? x : x2, 11, north ? z : z2, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        b.clear(x + 1, 0, z + 1, x2 - 1, 11, z2 - 1);
        b.fill(x + 1, 5, z + 1, x2 - 1, 5, z2 - 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
        b.fill(x + 1, 11, z + 1, x2 - 1, 11, z2 - 1, Blocks.STONE_BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        for (int i = 0; i <= 8; i += 2) {
            b.set(x + i, 12, z, stone);
            b.set(x + i, 12, z2, stone);
            b.set(x, 12, z + i, stone);
            b.set(x2, 12, z + i, stone);
        }
        int yardZ = north ? z2 : z;
        int yardX = west ? x2 : x;
        b.door(x + 4, 0, yardZ, Blocks.SPRUCE_DOOR, north ? Direction.SOUTH : Direction.NORTH);
        // Openings onto the wall walks that run off from the tower's two yard sides.
        int walkZ = north ? LO + 1 : HI - 1;
        int walkX = west ? LO + 1 : HI - 1;
        b.fill(yardX, 6, walkZ, yardX, 7, walkZ, Blocks.AIR.defaultBlockState());
        b.fill(walkX, 6, yardZ, walkX, 7, yardZ, Blocks.AIR.defaultBlockState());
        // Arrow slits on the field sides.
        int fieldX = west ? x : x2;
        int fieldZ = north ? z : z2;
        b.fill(fieldX, 7, z + 2, fieldX, 8, z + 2, Blocks.AIR.defaultBlockState());
        b.fill(x + 6, 7, fieldZ, x + 6, 8, fieldZ, Blocks.AIR.defaultBlockState());
        b.fill(fieldX, 2, z + 6, fieldX, 3, z + 6, Blocks.AIR.defaultBlockState());
        int ladderX = west ? x + 1 : x2 - 1;
        b.fill(ladderX, 0, z + 4, ladderX, 11, z + 4, Blocks.LADDER.defaultBlockState()
                .setValue(LadderBlock.FACING, west ? Direction.EAST : Direction.WEST));
        b.set(x + 4, 6, z + 4, Blocks.LANTERN.defaultBlockState());
        b.set(x + 4, 12, z + 4, Blocks.LANTERN.defaultBlockState());
        b.set(x + 4, 4, z + 4, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        int barrelX = west ? x2 - 1 : x + 1;
        b.set(barrelX, 0, north ? z + 1 : z2 - 1, Blocks.BARREL.defaultBlockState());
        b.set(barrelX, 6, north ? z2 - 1 : z + 1, Blocks.BARREL.defaultBlockState());
    }

    /**
     * The keep: a ground storey with a hall and three barred dungeon cells along the north wall, quarters above, an
     * armoury on top and a crenellated roof, joined by a ladder in the south-east corner.
     */
    private static void keep(BuildHelper b, RandomSource patches, RandomSource random) {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState trim = Blocks.POLISHED_ANDESITE.defaultBlockState();
        BlockState floor = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int x = KEEP_X1; x <= KEEP_X2; x++) {
            for (int z = KEEP_Z1; z <= KEEP_Z2; z++) {
                boolean edge = x == KEEP_X1 || x == KEEP_X2 || z == KEEP_Z1 || z == KEEP_Z2;
                if (edge) {
                    for (int y = 0; y <= KEEP_TOP; y++) {
                        b.set(x, y, z, brick(patches));
                    }
                }
            }
        }
        for (int[] c : new int[][]{{KEEP_X1, KEEP_Z1}, {KEEP_X2, KEEP_Z1}, {KEEP_X1, KEEP_Z2}, {KEEP_X2, KEEP_Z2}}) {
            b.fill(c[0], 0, c[1], c[0], KEEP_TOP, c[1], Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        }
        b.walls(KEEP_X1, 5, KEEP_Z1, KEEP_X2, 5, KEEP_Z2, trim);
        b.walls(KEEP_X1, 10, KEEP_Z1, KEEP_X2, 10, KEEP_Z2, trim);
        b.fill(KEEP_X1 + 1, -1, KEEP_Z1 + 1, KEEP_X2 - 1, -1, KEEP_Z2 - 1, Blocks.POLISHED_ANDESITE.defaultBlockState());
        b.fill(KEEP_X1 + 1, 5, KEEP_Z1 + 1, KEEP_X2 - 1, 5, KEEP_Z2 - 1, floor);
        b.fill(KEEP_X1 + 1, 10, KEEP_Z1 + 1, KEEP_X2 - 1, 10, KEEP_Z2 - 1, floor);
        b.fill(KEEP_X1 + 1, KEEP_TOP, KEEP_Z1 + 1, KEEP_X2 - 1, KEEP_TOP, KEEP_Z2 - 1, stone);
        for (int i = 0; i <= KEEP_X2 - KEEP_X1; i += 2) {
            b.set(KEEP_X1 + i, KEEP_TOP + 1, KEEP_Z1, stone);
            b.set(KEEP_X1 + i, KEEP_TOP + 1, KEEP_Z2, stone);
            b.set(KEEP_X1, KEEP_TOP + 1, KEEP_Z1 + i, stone);
            b.set(KEEP_X2, KEEP_TOP + 1, KEEP_Z1 + i, stone);
        }
        // The gate into the hall, and windows on the upper storeys.
        b.fill(GATE_X - 1, 0, KEEP_Z2, GATE_X + 1, 2, KEEP_Z2, Blocks.AIR.defaultBlockState());
        b.set(GATE_X - 2, 2, KEEP_Z2 + 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        b.set(GATE_X + 2, 2, KEEP_Z2 + 1, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        b.set(GATE_X - 2, 3, KEEP_Z2 + 1, Blocks.STONE_BRICK_WALL.defaultBlockState());
        b.set(GATE_X + 2, 3, KEEP_Z2 + 1, Blocks.STONE_BRICK_WALL.defaultBlockState());
        for (int y : new int[]{7, 12}) {
            for (int i : new int[]{3, 7, 11}) {
                b.fill(KEEP_X1 + i, y, KEEP_Z1, KEEP_X1 + i, y + 1, KEEP_Z1, Blocks.GLASS_PANE.defaultBlockState());
                b.fill(KEEP_X1 + i, y, KEEP_Z2, KEEP_X1 + i, y + 1, KEEP_Z2, Blocks.GLASS_PANE.defaultBlockState());
                b.fill(KEEP_X1, y, KEEP_Z1 + i, KEEP_X1, y + 1, KEEP_Z1 + i, Blocks.GLASS_PANE.defaultBlockState());
                b.fill(KEEP_X2, y, KEEP_Z1 + i, KEEP_X2, y + 1, KEEP_Z1 + i, Blocks.GLASS_PANE.defaultBlockState());
            }
        }
        // A ladder from the hall to the roof.
        int ladderX = KEEP_X2 - 1;
        int ladderZ = KEEP_Z2 - 1;
        b.fill(ladderX, 0, ladderZ, ladderX, KEEP_TOP, ladderZ, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));

        // Dungeon cells along the north wall, barred on the hall side.
        int cellZ2 = KEEP_Z1 + 4;
        b.fill(KEEP_X1 + 1, -1, KEEP_Z1 + 1, KEEP_X2 - 1, -1, cellZ2 - 1, Blocks.COBBLESTONE.defaultBlockState());
        b.fill(KEEP_X1 + 1, 0, cellZ2, KEEP_X2 - 1, 4, cellZ2, Blocks.IRON_BARS.defaultBlockState());
        for (int x : new int[]{23, 27}) {
            b.fill(x, 0, KEEP_Z1 + 1, x, 4, cellZ2, stone);
        }
        b.fill(KEEP_X1 + 1, 4, KEEP_Z1 + 1, KEEP_X2 - 1, 4, cellZ2 - 1, Blocks.COBBLESTONE.defaultBlockState());
        for (int x : new int[]{20, 24, 28}) {
            b.set(x, 0, KEEP_Z1 + 1, Blocks.HAY_BLOCK.defaultBlockState());
            b.set(x + 1, 3, KEEP_Z1 + 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }

        // The hall: a long table, the warlord's seat and braziers.
        b.fill(24, 0, 17, 26, 0, 21, Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
        b.set(25, 0, 16, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        for (int[] c : new int[][]{{20, 17}, {30, 17}, {20, 21}, {30, 21}}) {
            b.set(c[0], 0, c[1], Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        }
        b.chest(19, 0, 22, Direction.EAST, STORES, random);

        // Quarters on the first floor.
        for (int x : new int[]{20, 22, 28, 30}) {
            b.bed(x, 6, 12, x % 4 == 0 ? Blocks.GRAY_BED : Blocks.BLACK_BED, Direction.NORTH);
        }
        b.chest(KEEP_X1 + 1, 6, KEEP_Z2 - 2, Direction.EAST, LOOT, random);
        b.set(KEEP_X1 + 1, 6, KEEP_Z2 - 1, Blocks.BARREL.defaultBlockState());
        b.set(25, 9, 17, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));

        // The armoury on top.
        b.set(KEEP_X1 + 1, 11, KEEP_Z1 + 1, Blocks.ANVIL.defaultBlockState());
        b.set(KEEP_X1 + 2, 11, KEEP_Z1 + 1, Blocks.SMITHING_TABLE.defaultBlockState());
        b.set(KEEP_X1 + 3, 11, KEEP_Z1 + 1, Blocks.FLETCHING_TABLE.defaultBlockState());
        b.chest(KEEP_X2 - 1, 11, KEEP_Z1 + 1, Direction.SOUTH, LOOT, random);
        b.set(KEEP_X2 - 2, 11, KEEP_Z1 + 1, Blocks.BARREL.defaultBlockState());
        b.set(25, 14, 17, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        b.set(25, KEEP_TOP + 1, 17, Blocks.LANTERN.defaultBlockState());
    }

    /** A smithy against the east side of the keep. */
    private static void forge(BuildHelper b, RandomSource random) {
        b.fill(35, -1, 18, 40, -1, 23, Blocks.COBBLESTONE.defaultBlockState());
        for (int[] c : new int[][]{{35, 18}, {40, 18}, {35, 23}, {40, 23}}) {
            b.fill(c[0], 0, c[1], c[0], 3, c[1], Blocks.DARK_OAK_LOG.defaultBlockState());
        }
        b.fill(35, 4, 18, 40, 4, 23, Blocks.DARK_OAK_SLAB.defaultBlockState());
        b.set(39, 0, 19, Blocks.BLAST_FURNACE.defaultBlockState());
        b.set(39, 0, 20, Blocks.ANVIL.defaultBlockState());
        b.set(39, 0, 21, Blocks.SMITHING_TABLE.defaultBlockState());
        b.set(39, 0, 22, Blocks.GRINDSTONE.defaultBlockState());
        b.set(36, 0, 19, Blocks.LAVA_CAULDRON.defaultBlockState());
        b.barrel(36, 0, 22, STORES, random);
        b.barrel(37, 0, 22, STORES, random);
        b.set(37, 3, 20, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    /** A small well with a roof. */
    private static void well(BuildHelper b, int x, int z) {
        b.fill(x, -1, z, x + 3, -1, z + 3, Blocks.COBBLESTONE.defaultBlockState());
        b.walls(x, 0, z, x + 3, 0, z + 3, Blocks.COBBLESTONE.defaultBlockState());
        b.fill(x + 1, -1, z + 1, x + 2, 0, z + 2, Blocks.WATER.defaultBlockState());
        b.fill(x + 1, -3, z + 1, x + 2, -2, z + 2, Blocks.WATER.defaultBlockState());
        for (int[] c : new int[][]{{x, z}, {x + 3, z}, {x, z + 3}, {x + 3, z + 3}}) {
            b.fill(c[0], 1, c[1], c[0], 2, c[1], Blocks.DARK_OAK_FENCE.defaultBlockState());
        }
        b.fill(x, 3, z, x + 3, 3, z + 3, Blocks.DARK_OAK_SLAB.defaultBlockState());
    }

    /** A fenced pen for war beasts in the south-east of the courtyard. */
    private static void pen(BuildHelper b) {
        BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
        b.walls(36, 0, 28, 44, 1, 37, fence);
        b.fill(36, 0, 32, 36, 1, 33, Blocks.AIR.defaultBlockState());
        b.set(36, 0, 32, Blocks.DARK_OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.WEST));
        b.set(36, 0, 33, Blocks.DARK_OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.WEST));
        b.fill(37, -1, 29, 43, -1, 36, Blocks.COARSE_DIRT.defaultBlockState());
        b.fill(42, 0, 35, 43, 0, 36, Blocks.HAY_BLOCK.defaultBlockState());
        b.set(43, 1, 36, Blocks.HAY_BLOCK.defaultBlockState());
    }

    /** Stone bricks, here and there cracked or mossy. */
    private static BlockState brick(RandomSource patches) {
        int roll = patches.nextInt(10);
        return (roll == 0 ? Blocks.CRACKED_STONE_BRICKS : roll == 1 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
    }
}
