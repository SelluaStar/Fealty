package com.selluastar.fealty.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.network.OpenQuestScreenPayload;
import com.selluastar.fealty.network.QuestView;
import com.selluastar.fealty.network.SyncQuestsPayload;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.PlayerRepData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;


/** Keeps the client's journal and tracker in step with the player's accepted quests. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class QuestSync {
    private static final Map<UUID, Integer> LAST_SENT = new HashMap<>();

    private QuestSync() {
    }

    /** Send the full quest list now. */
    public static void sync(ServerPlayer player) {
        List<QuestView> views = build(player);
        LAST_SENT.put(player.getUUID(), views.hashCode());
        FealtyNetwork.send(player, new SyncQuestsPayload(views));
    }

    /** Send the quest list only if it changed since the last send (counts, readiness, waypoints). */
    public static void syncIfChanged(ServerPlayer player) {
        List<QuestView> views = build(player);
        int hash = views.hashCode();
        Integer last = LAST_SENT.get(player.getUUID());
        if (last == null || last != hash) {
            LAST_SENT.put(player.getUUID(), hash);
            FealtyNetwork.send(player, new SyncQuestsPayload(views));
        }
    }

    public static List<QuestView> build(ServerPlayer player) {
        PlayerRepData data = RepManager.data(player);
        List<QuestView> views = new ArrayList<>();
        Set<UUID> live = new HashSet<>();
        for (QuestContext ctx : QuestManager.activeContexts(player)) {
            live.add(ctx.quest().instanceId());
            views.add(view(ctx, data.isTracked(ctx.quest().instanceId())));
        }
        data.retainTracked(live);
        return views;
    }

    public static QuestView view(QuestContext ctx, boolean tracked) {
        RepQuestDefinition def = ctx.definition();
        QuestObjective objective = def.objective();
        boolean ready = objective.canTurnIn(ctx);
        Optional<QuestView.Waypoint> waypoint = ready ? returnWaypoint(ctx) : objective.waypoint(ctx);
        long expires = def.timeLimit() > 0 ? ctx.quest().startTime() + def.timeLimit() : -1L;
        return new QuestView(ctx.quest().instanceId(), ctx.quest().questId(), def.title(), def.description(), giverName(ctx),
                placeName(ctx), objective.progress(ctx),
                ready ? OpenQuestScreenPayload.Status.READY : OpenQuestScreenPayload.Status.ACTIVE,
                waypoint, expires, tracked, def.reward().rep(), def.difficulty());
    }

    /** Where to hand the quest in: the giver's home if known, else the village. */
    private static Optional<QuestView.Waypoint> returnWaypoint(QuestContext ctx) {
        if (ctx.state().contains("giver_pos")) {
            return net.minecraft.nbt.NbtUtils.readBlockPos(ctx.state(), "giver_pos")
                    .map(pos -> new QuestView.Waypoint(ctx.dimension(), pos, giverName(ctx)));
        }
        Optional<VillageRecord> village = ctx.village();
        return village.map(v -> new QuestView.Waypoint(v.dimension(),
                v.elder().home() != null ? v.elder().home() : v.center(), giverName(ctx)));
    }

    public static Component giverName(QuestContext ctx) {
        if (ctx.state().contains("giver_name")) {
            return Component.literal(ctx.state().getString("giver_name"));
        }
        if (Factions.isVillage(ctx.quest().giver())) {
            Optional<VillageRecord> village = FealtyWorldData.get(ctx.server()).village(ctx.quest().giver());
            if (village.isPresent() && !village.get().elder().name().isEmpty()) {
                return Component.literal(village.get().elder().name());
            }
            return Component.translatable("fealty.quest.giver.elder");
        }
        return Component.translatable("fealty.quest.giver.someone");
    }

    public static Component placeName(QuestContext ctx) {
        return Factions.displayName(ctx.server(), Factions.isVillage(ctx.quest().giver()) ? ctx.quest().giver() : ctx.quest().faction());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LAST_SENT.clear();
        QuestManager.clearPending();
    }
}
