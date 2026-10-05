package com.selluastar.fealty.quest.type;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.quest.ItemRequirement;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.quest.QuestType;
import com.selluastar.fealty.registry.ModQuestTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** {@code fealty:fetch}: bring crops, crafted goods or ore the village needs. */
public record FetchObjective(List<ItemRequirement> items) implements QuestObjective {
    public static final MapCodec<FetchObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemRequirement.CODEC.listOf().fieldOf("items").forGetter(FetchObjective::items)
    ).apply(i, FetchObjective::new));

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.FETCH.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        return true;
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        List<Component> lines = new ArrayList<>();
        for (ItemRequirement req : items) {
            int have = Math.min(req.countIn(ctx.player()), req.count());
            lines.add(Component.translatable("fealty.quest.fetch.line", req.count(), req.displayName(), have, req.count())
                    .withStyle(have >= req.count() ? ChatFormatting.DARK_GREEN : ChatFormatting.BLACK));
        }
        return lines;
    }

    @Override
    public List<Component> preview() {
        List<Component> lines = new ArrayList<>();
        for (ItemRequirement req : items) {
            lines.add(Component.translatable("fealty.quest.fetch.preview", req.count(), req.displayName()));
        }
        return lines;
    }

    @Override
    public boolean canTurnIn(QuestContext ctx) {
        for (ItemRequirement req : items) {
            if (req.countIn(ctx.player()) < req.count()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public TurnIn onTurnIn(QuestContext ctx) {
        if (!canTurnIn(ctx)) {
            return TurnIn.MISSING;
        }
        items.forEach(req -> req.take(ctx.player()));
        return TurnIn.COMPLETE;
    }
}
