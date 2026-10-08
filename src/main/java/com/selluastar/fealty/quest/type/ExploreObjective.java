package com.selluastar.fealty.quest.type;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.api.quest.QuestType;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.registry.ModQuestTypes;
import com.selluastar.fealty.util.Maps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

/**
 * {@code fealty:explore}: find a structure (by tag, e.g. {@code #minecraft:shipwreck}) and come back to tell of it.
 * The nearest one is picked when the quest starts; with {@code give_map} the player gets a map to it.
 */
public record ExploreObjective(TagKey<Structure> structure, int radius, int search, boolean giveMap, Component label)
        implements QuestObjective {
    public static final MapCodec<ExploreObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TagKey.hashedCodec(Registries.STRUCTURE).fieldOf("structure").forGetter(ExploreObjective::structure),
            Codec.intRange(4, 128).optionalFieldOf("radius", 24).forGetter(ExploreObjective::radius),
            Codec.intRange(1, 200).optionalFieldOf("search", 60).forGetter(ExploreObjective::search),
            Codec.BOOL.optionalFieldOf("give_map", true).forGetter(ExploreObjective::giveMap),
            ComponentSerialization.CODEC.optionalFieldOf("label", Component.translatable("fealty.objective.explore.place"))
                    .forGetter(ExploreObjective::label)
    ).apply(i, ExploreObjective::new));

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.EXPLORE.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        BlockPos found = ctx.level().findNearestMapStructure(structure, ctx.anchor(), search, false);
        if (found == null) {
            ctx.player().sendSystemMessage(Component.translatable("fealty.quest.explore.none", label));
            return false;
        }
        ctx.state().put("target", NbtUtils.writeBlockPos(found));
        if (giveMap) {
            Maps.give(ctx.player(), Maps.treasureMap(ctx.level(), found, MapDecorationTypes.RED_X,
                    Component.translatable("item.fealty.explore_map", label)));
        }
        return true;
    }

    private Optional<BlockPos> target(QuestContext ctx) {
        return NbtUtils.readBlockPos(ctx.state(), "target");
    }

    @Override
    public void tick(QuestContext ctx) {
        if (ctx.quest().isReady() || !ctx.player().level().dimension().equals(ctx.dimension())) {
            return;
        }
        target(ctx).ifPresent(pos -> {
            double dx = ctx.player().getX() - pos.getX();
            double dz = ctx.player().getZ() - pos.getZ();
            if (dx * dx + dz * dz <= (double) radius * radius) {
                Feedback.toast(ctx.player(), "pin", Component.translatable("fealty.toast.found"), label);
                ctx.setReady();
            }
        });
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        BlockPos pos = target(ctx).orElse(BlockPos.ZERO);
        return List.of(ctx.quest().isReady() ? Component.translatable("fealty.quest.explore.done", label)
                : Component.translatable("fealty.quest.explore.line", label, pos.getX(), pos.getZ()));
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.explore.preview", label));
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        return List.of(QuestView.Line.check(Component.translatable("fealty.objective.explore", label), ctx.quest().isReady()));
    }

    @Override
    public Optional<QuestView.Waypoint> waypoint(QuestContext ctx) {
        return target(ctx).map(pos -> new QuestView.Waypoint(ctx.dimension(), pos, label));
    }
}
