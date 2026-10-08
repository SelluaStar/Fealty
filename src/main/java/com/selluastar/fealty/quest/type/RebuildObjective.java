package com.selluastar.fealty.quest.type;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.quest.QuestType;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.registry.ModQuestTypes;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code fealty:rebuild}: part of a building near the village centre crumbles to rubble when the quest is accepted.
 * Break the rubble and put the right blocks back.
 */
public record RebuildObjective(int count, int radius) implements QuestObjective {
    public static final TagKey<Block> REBUILDABLE = TagKey.create(Registries.BLOCK, Fealty.id("rebuildable"));

    public static final MapCodec<RebuildObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 64).optionalFieldOf("count", 10).forGetter(RebuildObjective::count),
            Codec.intRange(4, 64).optionalFieldOf("radius", 28).forGetter(RebuildObjective::radius)
    ).apply(i, RebuildObjective::new));

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.REBUILD.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        Optional<VillageRecord> village = ctx.village();
        ServerLevel level = ctx.level();
        if (village.isEmpty() || !village.get().dimension().equals(level.dimension()) || !level.isLoaded(village.get().center())) {
            ctx.player().sendSystemMessage(Component.translatable("fealty.quest.rebuild.unloaded"));
            return false;
        }
        List<BlockPos> damaged = pickDamage(level, village.get(), level.getRandom());
        if (damaged.size() < Math.max(1, count / 2)) {
            ctx.player().sendSystemMessage(Component.translatable("fealty.quest.rebuild.nothing"));
            return false;
        }
        ListTag list = new ListTag();
        for (BlockPos pos : damaged) {
            CompoundTag entry = new CompoundTag();
            entry.put("pos", NbtUtils.writeBlockPos(pos));
            entry.put("state", NbtUtils.writeBlockState(level.getBlockState(pos)));
            entry.putBoolean("done", false);
            list.add(entry);
            level.setBlock(pos, ModBlocks.RUBBLE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        ctx.state().put("blocks", list);
        ctx.state().put("anchor", NbtUtils.writeBlockPos(damaged.getFirst()));
        return true;
    }

    private List<BlockPos> pickDamage(ServerLevel level, VillageRecord village, RandomSource random) {
        BlockPos center = village.center();
        BlockPos seed = null;
        for (int attempt = 0; attempt < 3000 && seed == null; attempt++) {
            BlockPos pos = center.offset(random.nextInt(radius * 2 + 1) - radius, random.nextInt(17) - 4, random.nextInt(radius * 2 + 1) - radius);
            if (isCandidate(level, village, pos)) {
                seed = pos;
            }
        }
        List<BlockPos> result = new ArrayList<>();
        if (seed == null) {
            return result;
        }
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(seed);
        seen.add(seed);
        while (!queue.isEmpty() && result.size() < count) {
            BlockPos pos = queue.poll();
            result.add(pos);
            for (Direction dir : Direction.values()) {
                BlockPos next = pos.relative(dir);
                if (seen.add(next) && isCandidate(level, village, next)) {
                    queue.add(next);
                }
            }
        }
        return result;
    }

    private static boolean isCandidate(ServerLevel level, VillageRecord village, BlockPos pos) {
        if (!village.contains(level.dimension(), pos) || !level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(REBUILDABLE) || PoiTypes.forState(state).isPresent() || level.getBlockEntity(pos) != null) {
            return false;
        }
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static ListTag blocks(QuestContext ctx) {
        return ctx.state().getList("blocks", Tag.TAG_COMPOUND);
    }

    private static int done(ListTag list) {
        int done = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).getBoolean("done")) {
                done++;
            }
        }
        return done;
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        ListTag list = blocks(ctx);
        BlockPos anchor = ctx.anchor();
        return List.of(Component.translatable("fealty.quest.rebuild.line", anchor.getX(), anchor.getY(), anchor.getZ(),
                done(list), list.size()));
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.rebuild.preview", count));
    }

    @Override
    public void onBlockPlaced(QuestContext ctx, ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.dimension().equals(ctx.dimension())) {
            return;
        }
        ListTag list = blocks(ctx);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.getBoolean("done") || !NbtUtils.readBlockPos(entry, "pos").map(pos::equals).orElse(false)) {
                continue;
            }
            BlockState original = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), entry.getCompound("state"));
            if (original.getBlock() == state.getBlock()) {
                entry.putBoolean("done", true);
                ctx.dirty();
                if (done(list) >= list.size()) {
                    ctx.setReady();
                }
            } else {
                ctx.player().displayClientMessage(Component.translatable("fealty.quest.rebuild.wrong", original.getBlock().getName()), true);
            }
            return;
        }
    }

    @Override
    public void tick(QuestContext ctx) {
        if (ctx.quest().isReady() || ctx.level().getGameTime() % 40 != 0 || !ctx.level().dimension().equals(ctx.dimension())) {
            return;
        }
        ListTag list = blocks(ctx);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.getBoolean("done")) {
                NbtUtils.readBlockPos(entry, "pos").ifPresent(pos -> ctx.level().sendParticles(ctx.player(),
                        ParticleTypes.HAPPY_VILLAGER, false, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.4, 0.4, 0.4, 0.0));
            }
        }
    }

    @Override
    public void cleanup(QuestContext ctx, boolean success) {
        if (success) {
            return;
        }
        ServerLevel level = ctx.questLevel();
        ListTag list = blocks(ctx);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Optional<BlockPos> pos = NbtUtils.readBlockPos(entry, "pos");
            if (entry.getBoolean("done") || pos.isEmpty() || !level.isLoaded(pos.get())) {
                continue;
            }
            BlockState current = level.getBlockState(pos.get());
            if (current.is(ModBlocks.RUBBLE.get()) || current.isAir()) {
                level.setBlock(pos.get(), NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), entry.getCompound("state")), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        ListTag list = blocks(ctx);
        return List.of(QuestView.Line.count(Component.translatable("fealty.objective.rebuild"), done(list), list.size()));
    }

}
