package com.selluastar.fealty.quest.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.MapCodec;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.quest.QuestType;
import com.selluastar.fealty.registry.ModQuestTypes;
import com.selluastar.fealty.war.Campaigns;
import com.selluastar.fealty.war.Strongholds;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;

/**
 * {@code fealty:raid_stronghold}: a lord's raid on a pillager stronghold, started from the Village Hall (see
 * {@link Campaigns}). It is the raid's entry in the tracker and journal: where to march, how many defenders are
 * left, and its reward when the stronghold falls.
 */
public record RaidStrongholdObjective() implements QuestObjective {
    public static final MapCodec<RaidStrongholdObjective> CODEC = MapCodec.unit(new RaidStrongholdObjective());

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.RAID_STRONGHOLD.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        return ctx.state().hasUUID("stronghold");
    }

    private static Optional<Strongholds.Entry> target(QuestContext ctx) {
        if (!ctx.state().hasUUID("stronghold")) {
            return Optional.empty();
        }
        UUID id = ctx.state().getUUID("stronghold");
        return Strongholds.get(ctx.server()).get(id);
    }

    private static Component name(QuestContext ctx) {
        return target(ctx).map(Strongholds.Entry::name).orElse(Component.translatable("fealty.stronghold.unknown"));
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        return progress(ctx).stream().map(line -> line.text()).toList();
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.raid_stronghold.preview"));
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        if (ctx.quest().isReady()) {
            return List.of(QuestView.Line.check(Component.translatable("fealty.objective.raid.won", name(ctx)), true));
        }
        List<QuestView.Line> lines = new ArrayList<>();
        Optional<int[]> battle = Campaigns.battleCount(ctx.player().getUUID());
        lines.add(QuestView.Line.check(Component.translatable("fealty.objective.raid.march", name(ctx)), battle.isPresent()));
        if (battle.isPresent()) {
            lines.add(QuestView.Line.count(Component.translatable("fealty.objective.raid.defenders"), battle.get()[0], battle.get()[1]));
        } else {
            lines.add(QuestView.Line.text(Component.translatable("fealty.objective.raid.defeat")));
        }
        int captives = target(ctx).map(Strongholds.Entry::captives).orElse(0);
        if (captives > 0) {
            lines.add(QuestView.Line.text(Component.translatable("fealty.objective.raid.captives", captives)));
        }
        return lines;
    }

    @Override
    public Optional<QuestView.Waypoint> waypoint(QuestContext ctx) {
        Optional<BlockPos> pos = target(ctx).map(Strongholds.Entry::pos).or(() -> NbtUtils.readBlockPos(ctx.state(), "target"));
        return pos.map(p -> new QuestView.Waypoint(ctx.dimension(), p, name(ctx)));
    }

    @Override
    public boolean completesOnReady() {
        return true;
    }
}
