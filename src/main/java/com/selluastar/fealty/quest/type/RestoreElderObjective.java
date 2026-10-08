package com.selluastar.fealty.quest.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.quest.QuestType;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.quest.ItemRequirement;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.registry.ModItems;
import com.selluastar.fealty.registry.ModQuestTypes;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.util.Maps;
import com.selluastar.fealty.village.ElderManager;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * {@code fealty:restore_elder}: the long road back for a Broken village. A neighbouring elder asks for the makings
 * of a new elder's mantle; hand them in to receive the Elder's Mantle, then place it on a villager of the
 * Broken village (sneak-use) to raise them as its new elder.
 */
public record RestoreElderObjective(List<ItemRequirement> items) implements QuestObjective {
    public static final MapCodec<RestoreElderObjective> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemRequirement.CODEC.listOf().fieldOf("items").forGetter(RestoreElderObjective::items)
    ).apply(i, RestoreElderObjective::new));

    private static final String QUEST_KEY = "fealty_restore_quest";
    private static final String TARGET_KEY = "fealty_restore_target";

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.RESTORE_ELDER.get();
    }

    /** The nearest Broken village a giver could send the player to restore. */
    public static Optional<VillageRecord> brokenNear(MinecraftServer server, VillageRecord giver) {
        int radius = FealtyConfig.RESTORE_SEARCH_RADIUS.get();
        VillageRecord best = null;
        double bestDistance = (double) radius * radius;
        for (VillageRecord record : FealtyWorldData.get(server).villages()) {
            if (record == giver || !record.isBroken() || !record.dimension().equals(giver.dimension())) {
                continue;
            }
            double d = record.center().distSqr(giver.center());
            if (d <= bestDistance) {
                bestDistance = d;
                best = record;
            }
        }
        return Optional.ofNullable(best);
    }

    @Override
    public boolean start(QuestContext ctx) {
        Optional<VillageRecord> giver = ctx.village();
        Optional<VillageRecord> target = giver.flatMap(g -> brokenNear(ctx.server(), g));
        if (target.isEmpty()) {
            ctx.player().sendSystemMessage(Component.translatable("fealty.quest.restore.none"));
            return false;
        }
        ctx.state().putString("target", target.get().id().toString());
        ctx.state().putString("target_name", target.get().name());
        ctx.state().put("anchor", NbtUtils.writeBlockPos(target.get().center()));
        ctx.state().putInt("stage", 0);
        return true;
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        List<Component> lines = new ArrayList<>();
        String target = ctx.state().getString("target_name");
        if (ctx.state().getInt("stage") == 0) {
            lines.add(Component.translatable("fealty.quest.restore.stage0", target));
            for (ItemRequirement req : items) {
                int have = Math.min(req.countIn(ctx.player()), req.count());
                lines.add(Component.translatable("fealty.quest.fetch.line", req.count(), req.displayName(), have, req.count()));
            }
        } else {
            lines.add(Component.translatable("fealty.quest.restore.stage1", target, ctx.anchor().getX(), ctx.anchor().getZ()));
        }
        return lines;
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.restore.preview"));
    }

    @Override
    public boolean canTurnIn(QuestContext ctx) {
        if (ctx.state().getInt("stage") != 0) {
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
        ItemStack mantle = new ItemStack(ModItems.ELDERS_MANTLE.get());
        CompoundTag tag = new CompoundTag();
        tag.putUUID(QUEST_KEY, ctx.quest().instanceId());
        tag.putString(TARGET_KEY, ctx.state().getString("target"));
        mantle.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        mantle.set(DataComponents.CUSTOM_NAME, Component.translatable("item.fealty.elders_mantle.for", ctx.state().getString("target_name")));
        Maps.give(ctx.player(), mantle);
        ctx.state().putInt("stage", 1);
        ctx.dirty();
        return TurnIn.PROGRESS;
    }

    @Override
    public boolean completesOnReady() {
        return true;
    }

    @Override
    public boolean onUseItemOnVillager(QuestContext ctx, Villager villager, ItemStack stack) {
        if (!stack.is(ModItems.ELDERS_MANTLE.get()) || ctx.state().getInt("stage") != 1) {
            return false;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID(QUEST_KEY) || !tag.getUUID(QUEST_KEY).equals(ctx.quest().instanceId())) {
            return false;
        }
        ResourceLocation target = ResourceLocation.tryParse(ctx.state().getString("target"));
        Optional<ResourceLocation> villagerVillage = FactionResolver.factionOf(villager);
        if (target == null || villagerVillage.isEmpty() || !villagerVillage.get().equals(target)) {
            ctx.player().displayClientMessage(Component.translatable("fealty.quest.restore.wrong_village", ctx.state().getString("target_name")), true);
            return true;
        }
        Optional<VillageRecord> record = FealtyWorldData.get(ctx.server()).village(target);
        if (record.isEmpty() || !record.get().isBroken()) {
            stack.shrink(1);
            ctx.setReady();
            return true;
        }
        ElderManager.restore(ctx.server(), record.get(), Optional.of(villager));
        stack.shrink(1);
        RepManager.meet(ctx.player(), target);
        RepManager.applySource(ctx.player(), target, RepSources.LIBERATION);
        FealtyEvents.fire(ctx.player(), FealtyEvents.ELDER_RESTORED);
        ctx.setReady();
        return true;
    }

    @Override
    public void cleanup(QuestContext ctx, boolean success) {
        if (success) {
            return;
        }
        for (ItemStack stack : ctx.player().getInventory().items) {
            if (stack.is(ModItems.ELDERS_MANTLE.get())) {
                CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (tag.hasUUID(QUEST_KEY) && tag.getUUID(QUEST_KEY).equals(ctx.quest().instanceId())) {
                    stack.setCount(0);
                }
            }
        }
    }

    @Override
    public List<QuestView.Line> progress(QuestContext ctx) {
        List<QuestView.Line> lines = new ArrayList<>();
        String target = ctx.state().getString("target_name");
        if (ctx.state().getInt("stage") == 0) {
            for (ItemRequirement req : items) {
                lines.add(QuestView.Line.count(Component.translatable("fealty.objective.bring", req.displayName()), req.countIn(ctx.player()), req.count()));
            }
            lines.add(QuestView.Line.text(Component.translatable("fealty.objective.restore_mantle")));
        } else {
            lines.add(QuestView.Line.check(Component.translatable("fealty.objective.restore", target), ctx.quest().isReady()));
        }
        return lines;
    }

    @Override
    public java.util.Optional<QuestView.Waypoint> waypoint(QuestContext ctx) {
        if (ctx.state().getInt("stage") == 0) {
            return java.util.Optional.empty();
        }
        return QuestObjective.super.waypoint(ctx);
    }

}
