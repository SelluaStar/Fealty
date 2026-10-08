package com.selluastar.fealty.quest.type;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.quest.QuestType;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.registry.ModQuestTypes;
import com.selluastar.fealty.util.Maps;
import com.selluastar.fealty.util.SpawnSpots;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

/**
 * {@code fealty:retrieve}: something was lost out in the wilds. A small site (a buried {@code cache}, a wrecked
 * {@code cart}, or a guarded bandit {@code stash}) appears {@code min_distance} to {@code max_distance} blocks away
 * when the player gets near; the item inside must be brought back to the giver.
 */
public record RetrieveObjective(Component itemName, ResourceLocation item, String site, int minDistance, int maxDistance,
                                boolean giveMap) implements QuestObjective {
    public static final MapCodec<RetrieveObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("item_name").forGetter(RetrieveObjective::itemName),
            ResourceLocation.CODEC.optionalFieldOf("item", ResourceLocation.withDefaultNamespace("book")).forGetter(RetrieveObjective::item),
            Codec.STRING.optionalFieldOf("site", "cache").forGetter(RetrieveObjective::site),
            Codec.intRange(16, 2000).optionalFieldOf("min_distance", 60).forGetter(RetrieveObjective::minDistance),
            Codec.intRange(16, 4000).optionalFieldOf("max_distance", 160).forGetter(RetrieveObjective::maxDistance),
            Codec.BOOL.optionalFieldOf("give_map", true).forGetter(RetrieveObjective::giveMap)
    ).apply(i, RetrieveObjective::new));

    private static final String TAG = "fealty_quest";

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.RETRIEVE.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        RandomSource random = ctx.level().getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        int distance = minDistance + random.nextInt(Math.max(1, maxDistance - minDistance));
        BlockPos anchor = ctx.anchor();
        BlockPos column = new BlockPos(anchor.getX() + Mth.floor(Math.cos(angle) * distance), 0,
                anchor.getZ() + Mth.floor(Math.sin(angle) * distance));
        ctx.state().put("site", NbtUtils.writeBlockPos(column));
        ctx.state().putBoolean("placed", false);
        if (giveMap) {
            Maps.give(ctx.player(), Maps.treasureMap(ctx.level(), column, MapDecorationTypes.RED_X,
                    Component.translatable("item.fealty.retrieve_map", itemName)));
        }
        return true;
    }

    private Optional<BlockPos> site(QuestContext ctx) {
        return NbtUtils.readBlockPos(ctx.state(), "site");
    }

    /** The quest item, marked with the quest so only it counts. */
    private ItemStack questItem(QuestContext ctx) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getOptional(item).orElse(Items.BOOK));
        stack.set(DataComponents.CUSTOM_NAME, itemName.copy().withStyle(s -> s.withItalic(false).withColor(0xF2D675)));
        CompoundTag tag = new CompoundTag();
        tag.putUUID(TAG, ctx.quest().instanceId());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private boolean isQuestItem(QuestContext ctx, ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return false;
        }
        CompoundTag tag = data.copyTag();
        return tag.hasUUID(TAG) && tag.getUUID(TAG).equals(ctx.quest().instanceId());
    }

    private boolean holding(QuestContext ctx) {
        Inventory inventory = ctx.player().getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isQuestItem(ctx, inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick(QuestContext ctx) {
        if (!ctx.player().level().dimension().equals(ctx.dimension())) {
            return;
        }
        if (holding(ctx)) {
            if (!ctx.state().getBoolean("found")) {
                ctx.state().putBoolean("found", true);
                ctx.dirty();
                Feedback.toast(ctx.player(), "pin", Component.translatable("fealty.toast.found"), itemName);
            }
            return;
        }
        Optional<BlockPos> spot = site(ctx);
        if (spot.isEmpty()) {
            return;
        }
        ServerLevel level = ctx.level();
        double dx = ctx.player().getX() - spot.get().getX();
        double dz = ctx.player().getZ() - spot.get().getZ();
        double distSq = dx * dx + dz * dz;
        if (!ctx.state().getBoolean("placed") && distSq < 64 * 64 && level.isLoaded(spot.get())) {
            BlockPos placed = build(level, spot.get(), questItem(ctx), level.getRandom());
            ctx.state().put("site", NbtUtils.writeBlockPos(placed));
            ctx.state().putBoolean("placed", true);
            ctx.dirty();
        }
        if ("stash".equals(site) && ctx.state().getBoolean("placed") && !ctx.state().getBoolean("guarded") && distSq < 28 * 28) {
            ctx.state().putBoolean("guarded", true);
            ctx.dirty();
            for (int n = 0; n < 2; n++) {
                SpawnSpots.nearY(level, spot.get().getX() + level.getRandom().nextInt(7) - 3, spot.get().getZ() + level.getRandom().nextInt(7) - 3,
                        spot.get().getY(), 4).ifPresent(pos -> {
                    var bandit = ModEntities.BANDIT.get().create(level);
                    if (bandit != null) {
                        bandit.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0);
                        bandit.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
                        bandit.setTarget(ctx.player());
                        level.addFreshEntity(bandit);
                    }
                });
            }
        }
    }

    /** Build the site on dry ground near the column and put the item in its container. @return the container */
    private BlockPos build(ServerLevel level, BlockPos column, ItemStack questItem, RandomSource random) {
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        for (int tries = 0; tries < 12; tries++) {
            BlockPos probe = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    column.offset(random.nextInt(13) - 6, 0, random.nextInt(13) - 6));
            if (SpawnSpots.isStandable(level, probe)) {
                ground = probe;
                break;
            }
        }
        BlockPos container;
        switch (site) {
            case "cart" -> {
                container = ground;
                level.setBlock(container, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), 3);
                level.setBlock(ground.east(), Blocks.HAY_BLOCK.defaultBlockState(), 3);
                level.setBlock(ground.west(), Blocks.OAK_FENCE.defaultBlockState(), 3);
                BlockState wheel = Blocks.DARK_OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true);
                level.setBlock(ground.north(), wheel.setValue(TrapDoorBlock.FACING, Direction.NORTH), 3);
                level.setBlock(ground.south(), wheel.setValue(TrapDoorBlock.FACING, Direction.SOUTH), 3);
            }
            case "stash" -> {
                container = ground;
                level.setBlock(container, Blocks.CHEST.defaultBlockState(), 3);
                level.setBlock(ground.east(2), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 3);
                level.setBlock(ground.west(), Blocks.BARREL.defaultBlockState(), 3);
                level.setBlock(ground.north(2), Blocks.RED_WOOL.defaultBlockState(), 3);
            }
            default -> {
                // A buried cache under a little cairn.
                container = ground.below();
                level.setBlock(container, Blocks.CHEST.defaultBlockState(), 3);
                level.setBlock(ground.east(), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 3);
                level.setBlock(ground.east().above(), Blocks.COBBLESTONE_WALL.defaultBlockState(), 3);
                level.setBlock(ground.north().below(), Blocks.COARSE_DIRT.defaultBlockState(), 3);
                level.setBlock(ground.south().below(), Blocks.COARSE_DIRT.defaultBlockState(), 3);
                level.setBlock(ground.west().below(), Blocks.COARSE_DIRT.defaultBlockState(), 3);
            }
        }
        if (level.getBlockEntity(container) instanceof Container box) {
            int size = box.getContainerSize();
            box.setItem(random.nextInt(size), questItem);
            ItemStack[] filler = {new ItemStack(Items.BREAD, 1 + random.nextInt(3)), new ItemStack(Items.COAL, 2 + random.nextInt(4)),
                    new ItemStack(Items.STICK, 1 + random.nextInt(5)), new ItemStack(Items.EMERALD, 1 + random.nextInt(2))};
            for (ItemStack stack : filler) {
                int slot = random.nextInt(size);
                if (box.getItem(slot).isEmpty() && random.nextBoolean()) {
                    box.setItem(slot, stack);
                }
            }
        }
        return container;
    }

    @Override
    public boolean canTurnIn(QuestContext ctx) {
        return holding(ctx);
    }

    @Override
    public TurnIn onTurnIn(QuestContext ctx) {
        Inventory inventory = ctx.player().getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isQuestItem(ctx, inventory.getItem(slot))) {
                inventory.removeItemNoUpdate(slot);
                return TurnIn.COMPLETE;
            }
        }
        return TurnIn.MISSING;
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        BlockPos pos = site(ctx).orElse(BlockPos.ZERO);
        return List.of(ctx.state().getBoolean("found") ? Component.translatable("fealty.quest.retrieve.bring", itemName)
                : Component.translatable("fealty.quest.retrieve.line", itemName, pos.getX(), pos.getZ()));
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.retrieve.preview", itemName));
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        return List.of(QuestView.Line.check(Component.translatable("fealty.objective.retrieve", itemName), canTurnIn(ctx)));
    }

    @Override
    public Optional<QuestView.Waypoint> waypoint(QuestContext ctx) {
        return site(ctx).map(pos -> new QuestView.Waypoint(ctx.dimension(), pos, itemName));
    }

    @Override
    public List<UUID> markedEntities(QuestContext ctx) {
        return List.of();
    }
}
