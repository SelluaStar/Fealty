package com.selluastar.fealty.village;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.RepTier;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.data.TierManager;
import com.selluastar.fealty.dialogue.Speech;
import com.selluastar.fealty.guard.GarrisonManager;
import com.selluastar.fealty.lordship.LordshipManager;
import com.selluastar.fealty.mail.MailService;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.outlaw.TyrantEvent;
import com.selluastar.fealty.rep.FealtyCalendar;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.trade.VillagerBehaviors;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BellBlock;
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
    /** When each village last raised the alarm at each Hated player. */
    private static final Map<String, Long> ALARMS = new HashMap<>();
    private static final int ALARM_COOLDOWN = 6000;

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
        noteVisit(player, record);
        VillagerBehaviors.tickNearPlayer(player, record);
        TyrantEvent.onPlayerInVillage(player, record);
        // Village-wide upkeep runs at most every 5 seconds per village, whoever is there.
        long time = level.getGameTime();
        Long last = LAST_VILLAGE_TICK.get(record.id());
        if (last == null || time - last >= 100) {
            LAST_VILLAGE_TICK.put(record.id(), time);
            ElderManager.tickVillage(level, record);
            LordshipManager.tickVillage(level, record);
            GarrisonManager.tickVillage(level, record);
            MailService.tickVillage(level, record);
        }
    }

    /** Remember the day the player was last in a village, so standing only fades with villages they stay away from. */
    private static void noteVisit(ServerPlayer player, VillageRecord village) {
        long day = FealtyCalendar.day(player.server);
        Long last = RepManager.data(player).visited().put(village.id(), day);
        if (last == null || last != day) {
            FealtyWorldData.get(player.server).setDirty();
        }
    }

    private static void onEnter(ServerPlayer player, VillageRecord village) {
        VillageResolver.refresh(player.serverLevel(), village);
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
        if (tier.rank() <= TierManager.hated().rank() && !village.lord().isLord(player.getUUID()) && FealtyConfig.HATED_ARRIVAL.get()) {
            hatedArrival(player, village);
        }
    }

    /** A Hated player walks in: the bell tolls, villagers run for their homes and someone cries the alarm. */
    private static void hatedArrival(ServerPlayer player, VillageRecord village) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        String key = player.getUUID() + "|" + village.id();
        Long last = ALARMS.get(key);
        if (last != null && now - last < ALARM_COOLDOWN) {
            return;
        }
        ALARMS.put(key, now);
        Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), village.center(), 48, PoiManager.Occupancy.ANY);
        if (bell.isPresent() && level.getBlockState(bell.get()).getBlock() instanceof BellBlock block) {
            block.attemptToRing(level, bell.get(), null);
        }
        Villager crier = null;
        for (Villager villager : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(32), v -> v.isAlive() && !v.isSleeping())) {
            villager.getBrain().setMemoryWithExpiry(MemoryModuleType.HURT_BY, player.damageSources().playerAttack(player), 300);
            villager.getBrain().setMemoryWithExpiry(MemoryModuleType.HURT_BY_ENTITY, player, 300);
            if (crier == null || villager.distanceToSqr(player) < crier.distanceToSqr(player)) {
                crier = villager;
            }
        }
        if (crier != null) {
            Speech.bark(crier, "hated_arrival", player, 0);
        }
        Feedback.banner(player, Component.translatable("fealty.banner.hated_arrival"),
                Component.translatable("fealty.banner.hated_arrival.detail", village.name()), 0xC0392B, "skull");
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
        ALARMS.clear();
        VillageResolver.clearCache();
    }
}
