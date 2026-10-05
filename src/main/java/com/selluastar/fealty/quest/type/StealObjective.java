package com.selluastar.fealty.quest.type;

import com.selluastar.fealty.network.QuestView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.quest.ItemRequirement;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.quest.QuestType;
import com.selluastar.fealty.registry.ModQuestTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** {@code fealty:steal}: lift items from village containers (unseen, if the guild insists) and bring them back. */
public record StealObjective(List<ItemRequirement> items, boolean unseen) implements QuestObjective {
    public static final MapCodec<StealObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemRequirement.CODEC.listOf().fieldOf("items").forGetter(StealObjective::items),
            Codec.BOOL.optionalFieldOf("unseen", true).forGetter(StealObjective::unseen)
    ).apply(i, StealObjective::new));

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.STEAL.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        return true;
    }

    private static int progress(QuestContext ctx, int index) {
        return ctx.state().getInt("stolen_" + index);
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        List<Component> lines = new ArrayList<>();
        for (int k = 0; k < items.size(); k++) {
            ItemRequirement req = items.get(k);
            int done = Math.min(progress(ctx, k), req.count());
            lines.add(Component.translatable(unseen ? "fealty.quest.steal.line_unseen" : "fealty.quest.steal.line",
                    req.count(), req.displayName(), done, req.count()).withStyle(done >= req.count() ? ChatFormatting.DARK_GREEN : ChatFormatting.BLACK));
        }
        if (ctx.quest().isReady()) {
            lines.add(Component.translatable("fealty.quest.steal.bring"));
        }
        return lines;
    }

    @Override
    public List<Component> preview() {
        List<Component> lines = new ArrayList<>();
        for (ItemRequirement req : items) {
            lines.add(Component.translatable(unseen ? "fealty.quest.steal.preview_unseen" : "fealty.quest.steal.preview", req.count(), req.displayName()));
        }
        return lines;
    }

    @Override
    public void onTheft(QuestContext ctx, ResourceLocation village, Map<Item, Integer> stolen, boolean witnessed, boolean coffer) {
        if (ctx.quest().isReady()) {
            return;
        }
        if (unseen && witnessed) {
            ctx.player().displayClientMessage(Component.translatable("fealty.quest.steal.seen"), true);
            return;
        }
        boolean all = true;
        for (int k = 0; k < items.size(); k++) {
            ItemRequirement req = items.get(k);
            int add = 0;
            for (Map.Entry<Item, Integer> entry : stolen.entrySet()) {
                if (req.ingredient().test(new ItemStack(entry.getKey()))) {
                    add += entry.getValue();
                }
            }
            int now = Math.min(req.count(), progress(ctx, k) + add);
            ctx.state().putInt("stolen_" + k, now);
            all &= now >= req.count();
        }
        ctx.dirty();
        if (all) {
            ctx.setReady();
        }
    }

    @Override
    public boolean canTurnIn(QuestContext ctx) {
        if (!ctx.quest().isReady()) {
            return false;
        }
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

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        List<QuestView.Line> lines = new ArrayList<>();
        for (int k = 0; k < items.size(); k++) {
            ItemRequirement req = items.get(k);
            lines.add(QuestView.Line.count(Component.translatable(unseen ? "fealty.objective.steal_unseen" : "fealty.objective.steal",
                    req.displayName()), progress(ctx, k), req.count()));
        }
        if (ctx.quest().isReady()) {
            lines.add(QuestView.Line.text(Component.translatable("fealty.quest.steal.bring")));
        }
        return lines;
    }

}
