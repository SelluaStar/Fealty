package com.selluastar.fealty.village;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.lordship.LordshipManager;
import com.selluastar.fealty.outlaw.TyrantEvent;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.trade.VillagerBehaviors;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Notices when players walk into villages and drives per-village upkeep while someone is there. */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class VillageTracker {
    private static final Map<UUID, ResourceLocation> CURRENT = new HashMap<>();
    private static final Map<ResourceLocation, Long> LAST_VILLAGE_TICK = new HashMap<>();

    private VillageTracker() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Optional<VillageRecord> village = VillageResolver.villageAt(level, player.blockPosition());
        ResourceLocation now = village.map(VillageRecord::id).orElse(null);
        ResourceLocation before = CURRENT.get(player.getUUID());
        if (!Objects.equals(now, before)) {
            if (now == null) {
                CURRENT.remove(player.getUUID());
            } else {
                CURRENT.put(player.getUUID(), now);
                onEnter(player, village.get());
            }
        }
        if (village.isEmpty()) {
            return;
        }
        VillageRecord record = village.get();
        VillagerBehaviors.tickNearPlayer(player, record);
        TyrantEvent.onPlayerInVillage(player, record);
        // Village-wide upkeep runs at most every 5 seconds per village, whoever is there.
        long time = level.getGameTime();
        Long last = LAST_VILLAGE_TICK.get(record.id());
        if (last == null || time - last >= 100) {
            LAST_VILLAGE_TICK.put(record.id(), time);
            ElderManager.tickVillage(level, record);
            LordshipManager.tickVillage(level, record);
        }
    }

    private static void onEnter(ServerPlayer player, VillageRecord village) {
        RepManager.meet(player, village.id());
        int rep = RepManager.getRep(player, village.id());
        RepTier tier = RepManager.tierOf(rep);
        Component tierName = tier.displayName().copy().withColor(tier.color());
        Component message;
        if (village.lord().isLord(player.getUUID())) {
            message = Component.translatable("fealty.village.enter_lord", village.name(), tierName, rep);
        } else if (village.isBroken()) {
            message = Component.translatable("fealty.village.enter_broken", village.name(), tierName, rep);
        } else {
            message = Component.translatable("fealty.village.enter", village.name(), tierName, rep);
        }
        player.displayClientMessage(message, true);
    }

    /** The village the player is standing in, as of their last check (once a second). */
    public static Optional<ResourceLocation> currentVillage(ServerPlayer player) {
        return Optional.ofNullable(CURRENT.get(player.getUUID()));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CURRENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CURRENT.clear();
        LAST_VILLAGE_TICK.clear();
        VillageResolver.clearCache();
    }
}
