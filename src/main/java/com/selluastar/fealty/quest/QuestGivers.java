package com.selluastar.fealty.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.entity.QuestGiverEntity;
import com.selluastar.fealty.network.OpenQuestScreenPayload;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

/** Finds the quest giver behind an entity and helps build their screens. */
public final class QuestGivers {
    private QuestGivers() {
    }

    public static Optional<QuestGiver> forEntity(Entity entity) {
        if (entity instanceof QuestGiverEntity giver) {
            return Optional.of(giver.giver());
        }
        if (entity instanceof Villager villager && ChainManager.hasRole(villager)) {
            return Optional.of(ChainManager.NAMED_VILLAGER);
        }
        return Optional.empty();
    }

    public static void open(ServerPlayer player, Entity entity) {
        forEntity(entity).ifPresent(giver -> com.selluastar.fealty.network.FealtyNetwork.send(player, giver.screen(player, entity)));
    }

    /** The accepted quest for a giver, or the given offers. */
    public static List<OpenQuestScreenPayload.QuestEntry> entries(ServerPlayer player, ResourceLocation giverKey, List<ResourceLocation> offers) {
        List<OpenQuestScreenPayload.QuestEntry> entries = new ArrayList<>();
        Optional<QuestContext> active = QuestManager.context(player, giverKey);
        if (active.isPresent()) {
            entries.add(activeEntry(active.get()));
            return entries;
        }
        for (ResourceLocation id : offers) {
            QuestManager.offerEntry(id).ifPresent(entries::add);
        }
        return entries;
    }

    public static OpenQuestScreenPayload.QuestEntry activeEntry(QuestContext ctx) {
        RepQuestDefinition def = ctx.definition();
        boolean ready = def.objective().canTurnIn(ctx);
        List<Component> lines = new ArrayList<>(def.objective().describe(ctx));
        if (def.timeLimit() > 0) {
            long left = def.timeLimit() - (ctx.level().getGameTime() - ctx.quest().startTime());
            lines.add(Component.translatable("fealty.quest.time_left", Math.max(0, left / 1200)));
        }
        return new OpenQuestScreenPayload.QuestEntry(ctx.quest().questId(), def.title(), def.description(), lines,
                def.reward().rep(), def.difficulty(), ready ? OpenQuestScreenPayload.Status.READY : OpenQuestScreenPayload.Status.ACTIVE);
    }
}
