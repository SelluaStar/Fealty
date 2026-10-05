package com.selluastar.fealty.world;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.entity.KeeperEntity;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.registry.ModStructures;
import com.selluastar.fealty.village.VillageNames;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.storage.loot.LootTable;

/** The hidden hamlet: a hedged clearing with the Keeper's lodge, two cottages, a well and a garden. */
public class HiddenHamletPiece extends ScatteredFeaturePiece {
    public static final int SIZE = 31;
    private static final ResourceKey<LootTable> LODGE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Fealty.id("chests/hidden_hamlet"));
    private static final ResourceKey<LootTable> COTTAGE_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Fealty.id("chests/hamlet_cottage"));

    public HiddenHamletPiece(RandomSource random, int x, int z, Direction orientation) {
        super(ModStructures.HIDDEN_HAMLET_PIECE.get(), x, 64, z, SIZE, 16, SIZE, orientation);
    }

    public HiddenHamletPiece(CompoundTag tag) {
        super(ModStructures.HIDDEN_HAMLET_PIECE.get(), tag);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        if (!updateAverageGroundHeight(level, box, 0)) {
            return;
        }
        BuildHelper b = new BuildHelper(level, box, new BlockPos(boundingBox.minX(), boundingBox.minY(), boundingBox.minZ()));
        BlockState air = Blocks.AIR.defaultBlockState();

        // The clearing
        b.clear(0, 0, 0, SIZE - 1, 22, SIZE - 1);
        b.foundation(0, 0, SIZE - 1, SIZE - 1, Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        BlockState hedge = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int i = 1; i < SIZE - 1; i++) {
            b.fill(i, 0, 1, i, 1, 1, hedge);
            b.fill(1, 0, i, 1, 1, i, hedge);
            b.fill(SIZE - 2, 0, i, SIZE - 2, 1, i, hedge);
            if (i < 13 || i > 17) {
                b.fill(i, 0, SIZE - 2, i, 1, SIZE - 2, hedge);
            }
        }
        lampPost(b, 12, 29);
        lampPost(b, 18, 29);
        b.fill(14, -1, 19, 16, -1, 29, Blocks.DIRT_PATH.defaultBlockState());

        lodge(b, random);
        cottage(b, random, 3, 11, true);
        cottage(b, random, 22, 11, false);
        well(b);
        garden(b, random);
        lampPost(b, 13, 20);
        lampPost(b, 17, 20);

        // The Keeper
        BlockPos keeperPos = b.pos(15, 1, 13);
        if (b.inChunk(keeperPos)) {
            KeeperEntity keeper = ModEntities.KEEPER.get().create(level.getLevel());
            if (keeper != null) {
                keeper.moveTo(keeperPos.getX() + 0.5, keeperPos.getY(), keeperPos.getZ() + 0.5, 180F, 0F);
                keeper.setHome(keeperPos);
                keeper.setCustomName(Component.translatable("entity.fealty.keeper.named", VillageNames.personName(random)));
                keeper.setCustomNameVisible(true);
                keeper.setPersistenceRequired();
                level.addFreshEntityWithPassengers(keeper);
            }
        }
        b.set(15, 2, 13, air);
    }

    private static void lampPost(BuildHelper b, int x, int z) {
        b.fill(x, 0, z, x, 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
        b.set(x, 2, z, Blocks.LANTERN.defaultBlockState());
    }

    private static void lodge(BuildHelper b, RandomSource random) {
        BlockState logs = Blocks.SPRUCE_LOG.defaultBlockState();
        b.fill(10, -1, 8, 20, -1, 18, Blocks.COBBLESTONE.defaultBlockState());
        b.fill(10, 0, 8, 20, 0, 18, Blocks.STONE_BRICKS.defaultBlockState());
        b.fill(11, 0, 9, 19, 0, 17, Blocks.SPRUCE_PLANKS.defaultBlockState());
        b.walls(10, 1, 8, 20, 4, 18, Blocks.SPRUCE_PLANKS.defaultBlockState());
        b.fill(11, 1, 9, 19, 4, 17, Blocks.AIR.defaultBlockState());
        for (int[] c : new int[][]{{10, 8}, {20, 8}, {10, 18}, {20, 18}, {10, 13}, {20, 13}}) {
            b.fill(c[0], 0, c[1], c[0], 4, c[1], logs);
        }
        BlockState paneNS = Blocks.GLASS_PANE.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
        BlockState paneEW = Blocks.GLASS_PANE.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
        for (int z : new int[]{11, 15}) {
            b.fill(10, 2, z, 10, 3, z + 1, paneNS);
            b.fill(20, 2, z, 20, 3, z + 1, paneNS);
        }
        for (int x : new int[]{12, 17}) {
            b.fill(x, 2, 8, x + 1, 3, 8, paneEW);
            b.fill(x, 2, 18, x + 1, 3, 18, paneEW);
        }
        b.door(15, 1, 18, Blocks.SPRUCE_DOOR, Direction.NORTH);
        for (int k = 0; k <= 6; k++) {
            int x1 = 9 + k;
            int x2 = 21 - k;
            int z1 = 7 + k;
            int z2 = 19 - k;
            if (x1 > x2 || z1 > z2) {
                break;
            }
            if (x1 == x2 || z1 == z2) {
                b.fill(x1, 5 + k, z1, x2, 5 + k, z2, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            } else {
                b.walls(x1, 5 + k, z1, x2, 5 + k, z2, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            }
        }
        // Inside: the Keeper's study
        b.bed(12, 1, 10, Blocks.GREEN_BED, Direction.NORTH);
        b.fill(14, 1, 9, 18, 2, 9, Blocks.BOOKSHELF.defaultBlockState());
        b.set(14, 3, 9, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true));
        b.set(18, 3, 9, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 3));
        b.set(18, 1, 12, Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.WEST));
        b.chest(11, 1, 16, Direction.EAST, LODGE_LOOT, random);
        b.set(19, 1, 16, Blocks.BARREL.defaultBlockState());
        b.fill(13, 1, 12, 17, 1, 14, Blocks.GREEN_CARPET.defaultBlockState());
        b.set(15, 4, 13, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    private static void cottage(BuildHelper b, RandomSource random, int x1, int z1, boolean doorEast) {
        int x2 = x1 + 5;
        int z2 = z1 + 5;
        b.fill(x1, -1, z1, x2, 0, z2, Blocks.COBBLESTONE.defaultBlockState());
        b.walls(x1, 1, z1, x2, 3, z2, Blocks.OAK_PLANKS.defaultBlockState());
        b.fill(x1 + 1, 1, z1 + 1, x2 - 1, 3, z2 - 1, Blocks.AIR.defaultBlockState());
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            b.fill(c[0], 0, c[1], c[0], 3, c[1], Blocks.OAK_LOG.defaultBlockState());
        }
        BlockState paneEW = Blocks.GLASS_PANE.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
        b.set(x1 + 2, 2, z1, paneEW);
        b.set(x1 + 3, 2, z2, paneEW);
        int doorX = doorEast ? x2 : x1;
        b.door(doorX, 1, z1 + 2, Blocks.OAK_DOOR, doorEast ? Direction.WEST : Direction.EAST);
        for (int k = 0; k <= 3; k++) {
            int ax = x1 - 1 + k;
            int bx = x2 + 1 - k;
            int az = z1 - 1 + k;
            int bz = z2 + 1 - k;
            if (ax > bx || az > bz) {
                break;
            }
            Block roof = Blocks.SPRUCE_PLANKS;
            if (ax == bx || az == bz || k == 3) {
                b.fill(ax, 4 + k, az, bx, 4 + k, bz, roof.defaultBlockState());
            } else {
                b.walls(ax, 4 + k, az, bx, 4 + k, bz, roof.defaultBlockState());
            }
        }
        int inner = doorEast ? x1 + 1 : x2 - 1;
        b.bed(inner, 1, z1 + 4, Blocks.BROWN_BED, Direction.NORTH);
        b.set(doorEast ? x1 + 1 : x2 - 1, 1, z1 + 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        b.barrel(doorEast ? x1 + 2 : x2 - 2, 1, z1 + 1, COTTAGE_LOOT, random);
    }

    private static void well(BuildHelper b) {
        b.fill(19, -3, 21, 23, 0, 25, Blocks.COBBLESTONE.defaultBlockState());
        b.fill(20, -2, 22, 22, 0, 24, Blocks.WATER.defaultBlockState());
        for (int[] c : new int[][]{{19, 21}, {23, 21}, {19, 25}, {23, 25}}) {
            b.fill(c[0], 1, c[1], c[0], 2, c[1], Blocks.OAK_FENCE.defaultBlockState());
        }
        b.fill(19, 3, 21, 23, 3, 25, Blocks.SPRUCE_SLAB.defaultBlockState());
    }

    private static void garden(BuildHelper b, RandomSource random) {
        BlockState farmland = Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE);
        for (int x = 4; x <= 9; x++) {
            for (int z = 20; z <= 25; z++) {
                if (x == 6 && z == 22) {
                    b.set(x, -1, z, Blocks.WATER.defaultBlockState());
                    continue;
                }
                b.set(x, -1, z, farmland);
                Block crop = (x + z) % 3 == 0 ? Blocks.CARROTS : (x + z) % 3 == 1 ? Blocks.POTATOES : Blocks.WHEAT;
                b.set(x, 0, z, crop.defaultBlockState().setValue(CropBlock.AGE, random.nextInt(8)));
            }
        }
    }
}
