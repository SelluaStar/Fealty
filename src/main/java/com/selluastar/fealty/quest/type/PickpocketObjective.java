package com.selluastar.fealty.quest.type;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.quest.QuestType;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.registry.ModQuestTypes;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** {@code fealty:pickpocket}: pick villagers' pockets without being seen (sneak up behind them, empty hand). */
public record PickpocketObjective(int count) implements QuestObjective {
    public static final MapCodec<PickpocketObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 64).optionalFieldOf("count", 3).forGetter(PickpocketObjective::count)
    ).apply(i, PickpocketObjective::new));

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.PICKPOCKET.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        return true;
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        return List.of(Component.translatable("fealty.quest.pickpocket.line", count, Math.min(ctx.getInt("done"), count), count));
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.pickpocket.preview", count));
    }

    @Override
    public void onPickpocket(QuestContext ctx, ResourceLocation village, boolean witnessed) {
        if (witnessed || ctx.quest().isReady()) {
            return;
        }
        int done = ctx.getInt("done") + 1;
        ctx.putInt("done", done);
        if (done >= count) {
            ctx.setReady();
        }
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        return List.of(QuestView.Line.count(Component.translatable("fealty.objective.pickpocket"), ctx.getInt("done"), count));
    }

}
