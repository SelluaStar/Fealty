package com.selluastar.fealty.quest.type;

import com.selluastar.fealty.network.QuestView;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.quest.QuestType;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.registry.ModQuestTypes;
import com.selluastar.fealty.util.Maps;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

/** {@code fealty:clear_camp}: clear the nearest bandit camp by defeating its captain. */
public record ClearCampObjective(boolean giveMap, int searchRadius) implements QuestObjective {
    public static final MapCodec<ClearCampObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("give_map", true).forGetter(ClearCampObjective::giveMap),
            Codec.intRange(1, 200).optionalFieldOf("search_radius", 50).forGetter(ClearCampObjective::searchRadius)
    ).apply(i, ClearCampObjective::new));

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.CLEAR_CAMP.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        BlockPos camp = ctx.level().findNearestMapStructure(FealtyTags.Structures.BANDIT_CAMPS, ctx.anchor(), searchRadius, false);
        if (camp == null) {
            ctx.player().sendSystemMessage(Component.translatable("fealty.quest.clear_camp.none"));
            return false;
        }
        ctx.state().put("camp", NbtUtils.writeBlockPos(camp));
        if (giveMap) {
            ItemStack map = Maps.treasureMap(ctx.level(), camp, MapDecorationTypes.RED_X, Component.translatable("item.fealty.camp_map"));
            Maps.give(ctx.player(), map);
        }
        return true;
    }

    private Optional<BlockPos> camp(QuestContext ctx) {
        return NbtUtils.readBlockPos(ctx.state(), "camp");
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        if (ctx.quest().isReady()) {
            return List.of(Component.translatable("fealty.quest.clear_camp.done"));
        }
        BlockPos camp = camp(ctx).orElse(BlockPos.ZERO);
        return List.of(Component.translatable("fealty.quest.clear_camp.line", camp.getX(), camp.getZ()));
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.clear_camp.preview"));
    }

    @Override
    public void onKill(QuestContext ctx, LivingEntity victim) {
        if (victim.getType() == ModEntities.BANDIT_CAPTAIN.get()
                && camp(ctx).map(c -> c.distSqr(victim.blockPosition()) < 96 * 96).orElse(false)) {
            ctx.setReady();
        }
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        return List.of(QuestView.Line.check(Component.translatable("fealty.objective.clear_camp"), ctx.quest().isReady()));
    }

    @Override
    public java.util.Optional<QuestView.Waypoint> waypoint(QuestContext ctx) {
        return camp(ctx).map(pos -> new QuestView.Waypoint(ctx.dimension(), pos, Component.translatable("fealty.objective.camp")));
    }

}
