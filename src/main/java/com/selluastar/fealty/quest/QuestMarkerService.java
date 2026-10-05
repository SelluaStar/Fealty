package com.selluastar.fealty.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.chain.ChainManager;
import com.selluastar.fealty.entity.VillageElderEntity;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.network.OpenQuestScreenPayload;
import com.selluastar.fealty.network.QuestMarkersPayload;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Every two seconds, tells each player which elders and named villagers near them have work, so the client can
 * show ! and ? above their heads. (The Keeper and the fence are left out: their screens have story side effects.)
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class QuestMarkerService {
    private static final double RANGE = 32.0;
    private static final int MAX_GIVERS = 16;
    private static final Map<UUID, Integer> LAST_SENT = new HashMap<>();

    private QuestMarkerService() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 13 || player.isSpectator()) {
            return;
        }
        List<QuestMarkersPayload.Marker> markers = new ArrayList<>();
        List<Entity> givers = player.serverLevel().getEntities((Entity) null, player.getBoundingBox().inflate(RANGE),
                e -> e.isAlive() && (e instanceof VillageElderEntity || (e instanceof Villager v && ChainManager.hasRole(v))));
        int count = 0;
        for (Entity giver : givers) {
            if (count++ >= MAX_GIVERS) {
                break;
            }
            markerFor(player, giver).ifPresent(kind -> markers.add(new QuestMarkersPayload.Marker(giver.getId(), kind)));
        }
        int hash = markers.hashCode();
        Integer last = LAST_SENT.get(player.getUUID());
        if (last == null || last != hash) {
            LAST_SENT.put(player.getUUID(), hash);
            FealtyNetwork.send(player, new QuestMarkersPayload(markers));
        }
    }

    /** The marker a giver shows this player: ready, in progress, new work, or a letter to deliver. */
    public static Optional<QuestMarkersPayload.Kind> markerFor(ServerPlayer player, Entity entity) {
        Optional<QuestGiver> giver = QuestGivers.forEntity(entity);
        if (giver.isEmpty()) {
            return Optional.empty();
        }
        if (entity instanceof VillageElderEntity elder && elder.village() != null) {
            Optional<VillageRecord> village = FealtyWorldData.get(player.server).village(elder.village());
            if (village.isPresent() && QuestManager.hasLettersFor(player, village.get())) {
                return Optional.of(QuestMarkersPayload.Kind.LETTER);
            }
        }
        OpenQuestScreenPayload screen = giver.get().screen(player, entity);
        boolean active = false;
        boolean offer = false;
        for (OpenQuestScreenPayload.QuestEntry quest : screen.quests()) {
            switch (quest.status()) {
                case READY -> {
                    return Optional.of(QuestMarkersPayload.Kind.READY);
                }
                case ACTIVE -> active = true;
                case OFFER -> offer = true;
            }
        }
        if (active) {
            return Optional.of(QuestMarkersPayload.Kind.ACTIVE);
        }
        return offer ? Optional.of(QuestMarkersPayload.Kind.OFFER) : Optional.empty();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }
}
