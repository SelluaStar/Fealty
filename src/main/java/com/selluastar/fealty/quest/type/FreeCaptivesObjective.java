package com.selluastar.fealty.quest.type;

import java.util.List;
import java.util.Optional;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.Stronghold;
import com.selluastar.fealty.api.event.StrongholdEvent;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.quest.QuestType;
import com.selluastar.fealty.registry.ModQuestTypes;
import com.selluastar.fealty.util.Maps;
import com.selluastar.fealty.war.Scouting;
import com.selluastar.fealty.war.Strongholds;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

/**
 * {@code fealty:free_captives}: free villagers held in the nearest pillager camp (break the bars of their cage).
 * Comes with a map to the camp.
 */
public record FreeCaptivesObjective(int count, int searchRadius) implements QuestObjective {
    public static final MapCodec<FreeCaptivesObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 16).optionalFieldOf("count", 2).forGetter(FreeCaptivesObjective::count),
            Codec.intRange(1, 200).optionalFieldOf("search_radius", 50).forGetter(FreeCaptivesObjective::searchRadius)
    ).apply(i, FreeCaptivesObjective::new));

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.FREE_CAPTIVES.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        Pair<BlockPos, ResourceLocation> camp = Scouting.nearest(ctx.level(), Scouting.CAMPS, ctx.anchor(), searchRadius);
        if (camp == null) {
            ctx.player().sendSystemMessage(Component.translatable("fealty.quest.free_captives.none"));
            return false;
        }
        Strongholds.Entry entry = Strongholds.get(ctx.server()).register(ctx.level(), camp.getFirst(), Stronghold.Kind.CAMP, camp.getSecond(),
                Scouting.radiusOf(ctx.level(), camp.getSecond()), StrongholdEvent.Discovered.How.SCOUTED);
        ctx.state().put("camp", NbtUtils.writeBlockPos(entry.pos()));
        ItemStack map = Maps.treasureMap(ctx.level(), entry.pos(), MapDecorationTypes.RED_X, Component.translatable("item.fealty.captive_map"));
        Maps.give(ctx.player(), map);
        return true;
    }

    private Optional<BlockPos> camp(QuestContext ctx) {
        return NbtUtils.readBlockPos(ctx.state(), "camp");
    }

    public int freed(QuestContext ctx) {
        return ctx.state().getInt("freed");
    }

    /** A captive this player freed (anywhere: any camp's captives are someone's kin). */
    public void onFreed(QuestContext ctx) {
        if (ctx.quest().isReady()) {
            return;
        }
        ctx.state().putInt("freed", freed(ctx) + 1);
        ctx.dirty();
        if (freed(ctx) >= count) {
            ctx.setReady();
        }
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        if (ctx.quest().isReady()) {
            return List.of(Component.translatable("fealty.quest.free_captives.done"));
        }
        return List.of(Component.translatable("fealty.quest.free_captives.line", freed(ctx), count));
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.free_captives.preview", count));
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        return List.of(QuestView.Line.count(Component.translatable("fealty.objective.free_captives"), Math.min(freed(ctx), count), count));
    }

    @Override
    public Optional<QuestView.Waypoint> waypoint(QuestContext ctx) {
        return camp(ctx).map(pos -> new QuestView.Waypoint(ctx.dimension(), pos, Component.translatable("fealty.objective.camp")));
    }
}
