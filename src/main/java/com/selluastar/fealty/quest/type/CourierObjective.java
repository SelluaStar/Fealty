package com.selluastar.fealty.quest.type;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.FealtyTags;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.item.LetterInfo;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestObjective;
import com.selluastar.fealty.quest.QuestType;
import com.selluastar.fealty.registry.ModDataComponents;
import com.selluastar.fealty.registry.ModItems;
import com.selluastar.fealty.registry.ModQuestTypes;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.util.Maps;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * {@code fealty:courier}: carry a sealed letter to the trusting elder of another village. Completes on delivery
 * and also earns a little standing with the receiving village, tying villages (and Renown) together.
 */
public record CourierObjective() implements QuestObjective {
    public static final MapCodec<CourierObjective> CODEC = MapCodec.unit(new CourierObjective());

    @Override
    public QuestType<?> type() {
        return ModQuestTypes.COURIER.get();
    }

    @Override
    public boolean start(QuestContext ctx) {
        Optional<VillageRecord> origin = ctx.village();
        if (origin.isEmpty()) {
            return false;
        }
        Optional<VillageRecord> destination = findDestination(ctx.level(), origin.get(), ctx.level().getRandom());
        if (destination.isEmpty()) {
            ctx.player().sendSystemMessage(Component.translatable("fealty.quest.courier.none"));
            return false;
        }
        VillageRecord to = destination.get();
        ctx.state().putString("to", to.id().toString());
        ctx.state().putString("to_name", to.name());
        ctx.state().put("anchor", NbtUtils.writeBlockPos(to.center()));
        ItemStack letter = new ItemStack(ModItems.SEALED_LETTER.get());
        letter.set(ModDataComponents.LETTER.get(), new LetterInfo(origin.get().id(), origin.get().name(), to.id(), to.name(),
                to.center(), ctx.player().getUUID(), ctx.quest().instanceId()));
        Maps.give(ctx.player(), letter);
        return true;
    }

    static Optional<VillageRecord> findDestination(ServerLevel level, VillageRecord origin, RandomSource random) {
        int min = FealtyConfig.COURIER_MIN_DISTANCE.get();
        int max = FealtyConfig.COURIER_MAX_DISTANCE.get();
        List<VillageRecord> known = new ArrayList<>();
        for (VillageRecord record : FealtyWorldData.get(level.getServer()).villages()) {
            if (record != origin && record.dimension().equals(origin.dimension()) && record.hasElder() && !record.isBroken()) {
                double d = Math.sqrt(record.center().distSqr(origin.center()));
                if (d >= min && d <= max) {
                    known.add(record);
                }
            }
        }
        if (!known.isEmpty()) {
            return Optional.of(known.get(random.nextInt(known.size())));
        }
        if (!origin.dimension().equals(level.dimension())) {
            return Optional.empty();
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            BlockPos probe = origin.center().offset(Mth.floor(Math.cos(angle) * min), 0, Mth.floor(Math.sin(angle) * min));
            BlockPos found = level.findNearestMapStructure(FealtyTags.Structures.ELDER_VILLAGES, probe, 50, false);
            if (found == null || origin.bounds().isInside(found)) {
                continue;
            }
            double d = Math.sqrt(found.distSqr(origin.center()));
            if (d < min || d > max) {
                continue;
            }
            ChunkAccess chunk = level.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
            for (StructureStart start : chunk.getAllStarts().values()) {
                Holder<Structure> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(start.getStructure());
                if (start.isValid() && holder.is(FealtyTags.Structures.ELDER_VILLAGES)) {
                    Optional<VillageRecord> record = VillageResolver.villageForStart(level, start);
                    if (record.isPresent() && record.get().hasElder()) {
                        return record;
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Component> describe(QuestContext ctx) {
        BlockPos to = ctx.anchor();
        return List.of(Component.translatable("fealty.quest.courier.line", ctx.state().getString("to_name"), to.getX(), to.getZ()));
    }

    @Override
    public List<Component> preview() {
        return List.of(Component.translatable("fealty.quest.courier.preview"));
    }

    @Override
    public boolean completesOnReady() {
        return true;
    }

    /** The letter reached the right elder. */
    public static void deliver(QuestContext ctx, ItemStack letter) {
        letter.shrink(1);
        ResourceLocation to = ResourceLocation.tryParse(ctx.state().getString("to"));
        if (to != null) {
            RepManager.meet(ctx.player(), to);
            RepManager.applySource(ctx.player(), to, RepSources.COURIER);
        }
        FealtyEvents.fire(ctx.player(), FealtyEvents.COURIER_DELIVERED);
        ctx.setReady();
    }

    @Override
    public void cleanup(QuestContext ctx, boolean success) {
        if (success) {
            return;
        }
        for (ItemStack stack : ctx.player().getInventory().items) {
            LetterInfo info = stack.get(ModDataComponents.LETTER.get());
            if (info != null && info.quest().equals(ctx.quest().instanceId())) {
                stack.setCount(0);
            }
        }
    }
}
