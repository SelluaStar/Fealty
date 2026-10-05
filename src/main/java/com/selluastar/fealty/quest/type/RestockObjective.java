package com.selluastar.fealty.quest.type;

import com.selluastar.fealty.network.QuestView;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.quest.QuestType;
import com.selluastar.fealty.registry.ModQuestTypes;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** {@code fealty:restock}: place workstations ({@code #fealty:workstations}) in the village. */
public record RestockObjective(int count) implements QuestObjective {
    public static final MapCodec<RestockObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 64).optionalFieldOf("count", 3).forGetter(RestockObjective::count)
    ).apply(i, RestockObjective::new));

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.RESTOCK.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        return ctx.village().isPresent();
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        String name = ctx.village().map(VillageRecord::name).orElse("?");
        return List.of(Component.translatable("fealty.quest.restock.line", count, name, Math.min(ctx.getInt("placed"), count), count));
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.restock.preview", count));
    }

    @Override
    public void onBlockPlaced(QuestContext ctx, ServerLevel level, BlockPos pos, BlockState state) {
        if (ctx.quest().isReady() || !state.is(FealtyTags.Blocks.WORKSTATIONS)) {
            return;
        }
        if (ctx.village().map(v -> v.contains(level.dimension(), pos)).orElse(false)) {
            // Each spot counts once, so placing and breaking the same workstation does not finish the quest.
            long[] seen = ctx.state().getLongArray("positions");
            long key = pos.asLong();
            for (long p : seen) {
                if (p == key) {
                    return;
                }
            }
            long[] grown = java.util.Arrays.copyOf(seen, seen.length + 1);
            grown[seen.length] = key;
            ctx.state().putLongArray("positions", grown);
            int placed = ctx.getInt("placed") + 1;
            ctx.putInt("placed", placed);
            if (placed >= count) {
                ctx.setReady();
            }
        }
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        String name = ctx.village().map(VillageRecord::name).orElse("?");
        return List.of(QuestView.Line.count(Component.translatable("fealty.objective.restock", name), ctx.getInt("placed"), count));
    }

    @Override
    public java.util.Optional<QuestView.Waypoint> waypoint(QuestContext ctx) {
        return ctx.village().map(v -> new QuestView.Waypoint(v.dimension(), v.center(), Component.literal(v.name())));
    }

}
