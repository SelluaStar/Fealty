package com.selluastar.fealty.war;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.api.event.MenaceRaidEvent;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.mail.MailService;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageTracker;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Pillager strongholds left standing menace the villages near them: every few days, while a player is in such a
 * village, the pillagers march on it (a vanilla raid). Razing the stronghold, or the peace a victory wins, stops it.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class Menace {
    private static final int CHECK_INTERVAL = 1200;
    /** Once a village is due, the chance of a raid each minute a player is there. */
    private static final int CHANCE = 12;
    /** Ticks of Raid Omen before the raid begins (vanilla turns the omen into a raid when it runs out). */
    private static final int OMEN_TICKS = 300;

    private Menace() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % CHECK_INTERVAL == 611 && FealtyConfig.PILLAGER_MENACE.get()) {
            check(server);
        }
    }

    private static void check(MinecraftServer server) {
        long day = RepManager.day(server);
        FealtyWorldData world = FealtyWorldData.get(server);
        Set<ResourceLocation> considered = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            Optional<VillageRecord> found = VillageTracker.currentVillage(player).flatMap(world::village);
            if (found.isEmpty() || !considered.add(found.get().id())) {
                continue;
            }
            VillageRecord village = found.get();
            ServerLevel level = player.serverLevel();
            if (village.sites().lastMenaceDay() < 0) {
                // A village is left in peace for a while after it is first seen.
                village.sites().setLastMenaceDay(day);
                world.setDirty();
                continue;
            }
            if (!canRaid(level, village, day) || level.getRandom().nextInt(CHANCE) != 0) {
                continue;
            }
            Optional<Strongholds.Entry> menace = menaceOf(level, village, day);
            if (menace.isEmpty()) {
                continue;
            }
            village.sites().setLastMenaceDay(day);
            world.setDirty();
            start(level, village, menace.get(), player, day);
        }
    }

    /** Whether the village is due a raid and raids can happen here. */
    private static boolean canRaid(ServerLevel level, VillageRecord village, long day) {
        int min = FealtyConfig.MENACE_MIN_DAYS.get();
        int spread = Math.max(1, FealtyConfig.MENACE_MAX_DAYS.get() - min + 1);
        long gap = min + Math.floorMod(village.id().hashCode() * 31L + village.sites().lastMenaceDay(), spread);
        return day - village.sites().lastMenaceDay() >= gap && !village.sites().atPeace(day) && village.dimension().equals(level.dimension())
                && level.getDifficulty() != Difficulty.PEACEFUL && !level.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)
                && level.dimensionType().hasRaids() && level.getRaidAt(village.center()) == null;
    }

    /** The nearest stronghold standing within reach of a village. */
    public static Optional<Strongholds.Entry> menaceOf(ServerLevel level, VillageRecord village, long day) {
        List<Strongholds.Entry> near = Strongholds.get(level.getServer()).near(village.dimension(), village.center(), FealtyConfig.MENACE_RANGE.get());
        return near.stream().filter(e -> !e.isRazed(day)).findFirst();
    }

    /** The pillagers of a stronghold march on a village. @return whether a raid was set off */
    public static boolean start(ServerLevel level, VillageRecord village, Strongholds.Entry stronghold, ServerPlayer player, long day) {
        int omen = 1 + (day >= 30 ? 1 : 0) + (day >= 60 ? 1 : 0);
        MenaceRaidEvent event = NeoForge.EVENT_BUS.post(new MenaceRaidEvent(level, village.id(), stronghold.view(day), player, omen));
        if (event.isCanceled()) {
            return false;
        }
        player.setRaidOmenPosition(village.center());
        player.addEffect(new MobEffectInstance(MobEffects.RAID_OMEN, OMEN_TICKS, event.getOmenLevel() - 1));
        Component title = Component.translatable("fealty.menace.banner");
        Component detail = Component.translatable("fealty.menace.banner.detail", stronghold.name(), village.name());
        for (ServerPlayer other : level.players()) {
            if (village.contains(level.dimension(), other.blockPosition())) {
                Feedback.banner(other, title, detail, 0x5E3A87, "skull");
                Feedback.sound(other, SoundEvents.RAID_HORN, 0.6F, 0.8F);
            }
        }
        if (village.lord().uuid() != null) {
            MailService.fromVillage(level.getServer(), village.lord().uuid(), village, "pillager_menace", List.of(), 20 * 30, stronghold.name());
        }
        return true;
    }
}
