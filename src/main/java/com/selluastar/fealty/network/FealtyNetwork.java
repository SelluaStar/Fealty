package com.selluastar.fealty.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.rep.Factions;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.PlayerRepData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration and the helpers that keep the client's view of reputation current. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class FealtyNetwork {
    private FealtyNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Client-bound handlers are lambdas so the client classes they call are only loaded on the client.
        registrar.playToClient(SyncTiersPayload.TYPE, SyncTiersPayload.STREAM_CODEC,
                (payload, context) -> com.selluastar.fealty.client.ClientPayloads.tiers(payload));
        registrar.playToClient(SyncStandingsPayload.TYPE, SyncStandingsPayload.STREAM_CODEC,
                (payload, context) -> com.selluastar.fealty.client.ClientPayloads.standings(payload));
        registrar.playToClient(OpenQuestScreenPayload.TYPE, OpenQuestScreenPayload.STREAM_CODEC,
                (payload, context) -> com.selluastar.fealty.client.ClientPayloads.openQuestScreen(payload));
        registrar.playToClient(SyncVillageTradesPayload.TYPE, SyncVillageTradesPayload.STREAM_CODEC,
                (payload, context) -> com.selluastar.fealty.client.ClientPayloads.villageTrades(payload));
        registrar.playToServer(QuestActionPayload.TYPE, QuestActionPayload.STREAM_CODEC, QuestActionPayload::handle);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RepManager.data(player).setName(player.getGameProfile().getName());
            if (com.selluastar.fealty.config.FealtyConfig.GIVE_LEDGER.get() && RepManager.data(player).setFlag(Fealty.id("received_ledger"))) {
                com.selluastar.fealty.util.Maps.give(player, new net.minecraft.world.item.ItemStack(com.selluastar.fealty.registry.ModItems.LEDGER.get()));
            }
            syncAll(player);
        }
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            syncAll(event.getPlayer());
        } else {
            event.getPlayerList().getPlayers().forEach(FealtyNetwork::syncAll);
        }
    }

    /** Send the tier table and every standing the player has. */
    public static void syncAll(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncTiersPayload(TierManager.tiers()));
        PacketDistributor.sendToPlayer(player, SyncVillageTradesPayload.create());
        PlayerRepData data = RepManager.data(player);
        List<Standing> standings = new ArrayList<>();
        for (ResourceLocation faction : data.rep().keySet()) {
            standings.add(standing(player, faction));
        }
        PacketDistributor.sendToPlayer(player, new SyncStandingsPayload(standings, RepManager.renown(data), true));
    }

    public static void syncStanding(ServerPlayer player, ResourceLocation faction) {
        PacketDistributor.sendToPlayer(player, new SyncStandingsPayload(List.of(standing(player, faction)), RepManager.renown(player), false));
    }

    public static void syncRenown(ServerPlayer player, int renown) {
        PacketDistributor.sendToPlayer(player, new SyncStandingsPayload(List.of(), renown, false));
    }

    public static Standing standing(ServerPlayer player, ResourceLocation faction) {
        boolean village = Factions.isVillage(faction);
        boolean lord = false;
        if (village) {
            Optional<VillageRecord> record = FealtyWorldData.get(player.server).village(faction);
            lord = record.map(r -> r.lord().isLord(player.getUUID())).orElse(false);
        }
        return new Standing(faction, Factions.displayName(player.server, faction), RepManager.getRep(player, faction), village, lord);
    }
}
