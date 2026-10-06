package com.selluastar.fealty.war;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.event.CaptiveEvent;
import com.selluastar.fealty.api.event.VillageEvent;
import com.selluastar.fealty.dialogue.Speech;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.quest.QuestContext;
import com.selluastar.fealty.quest.QuestManager;
import com.selluastar.fealty.quest.type.FreeCaptivesObjective;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.util.SpawnSpots;
import com.selluastar.fealty.village.VillageNames;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Villagers held in pillager camps. Each knows the village it was taken from. Freed (by breaking the cage's bars, or
 * when a lord's warband takes the camp) they thank their rescuer and slip away, and turn up in their home village
 * the next time anyone is there.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class Captives {
    public static final String CAPTIVE_TAG = "fealty.captive";
    private static final String FREED_TAG = "fealty.freed";
    private static final String HOME_KEY = "fealty_home";
    private static final String CAMP_KEY = "fealty_camp";
    /** Freed captives this far from a village are counted as its own. */
    private static final double HOME_RANGE = 2000;
    private static final int LEAVE_TICKS = 80;
    private static final List<Departure> LEAVING = new ArrayList<>();

    private record Departure(ResourceKey<Level> dimension, UUID entity, long due, @Nullable ResourceLocation home) {
    }

    private Captives() {
    }

    public static boolean isCaptive(Entity entity) {
        return entity.getTags().contains(CAPTIVE_TAG);
    }

    /**
     * Put one or two captives in each cage of a camp.
     *
     * @param cages where each cage's captives stand, from the War Banner
     * @return how many were placed
     */
    public static int fillCages(ServerLevel level, BlockPos banner, BlockPos[] cages) {
        RandomSource random = level.getRandom();
        ResourceLocation home = nearestVillage(level, banner).map(VillageRecord::id).orElse(null);
        int placed = 0;
        for (BlockPos offset : cages) {
            BlockPos pos = banner.offset(offset);
            if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir()) {
                continue;
            }
            int count = 1 + random.nextInt(2);
            for (int i = 0; i < count; i++) {
                Villager villager = EntityType.VILLAGER.create(level);
                if (villager == null) {
                    continue;
                }
                villager.moveTo(pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY(), pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                        random.nextFloat() * 360F, 0);
                villager.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
                makeCaptive(villager, banner, home);
                villager.setCustomName(Component.literal(VillageNames.personName(random)));
                if (NeoForge.EVENT_BUS.post(new CaptiveEvent.Taken(villager, banner, home)).isCanceled()) {
                    villager.discard();
                    continue;
                }
                level.addFreshEntity(villager);
                placed++;
            }
        }
        return placed;
    }

    /** Mark a villager as held at a camp, taken from {@code home}. */
    public static void makeCaptive(Villager villager, BlockPos camp, @Nullable ResourceLocation home) {
        villager.setPersistenceRequired();
        villager.addTag(CAPTIVE_TAG);
        villager.getPersistentData().putLong(CAMP_KEY, camp.asLong());
        if (home != null) {
            villager.getPersistentData().putString(HOME_KEY, home.toString());
        }
    }

    /** Captives held within {@code radius} blocks of a place. */
    public static List<Villager> near(ServerLevel level, BlockPos pos, int radius) {
        return level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(radius), v -> v.isAlive() && isCaptive(v));
    }

    /** The village a captive was taken from: the one remembered, else the nearest known one. */
    @Nullable
    public static ResourceLocation home(ServerLevel level, Entity captive) {
        String home = captive.getPersistentData().getString(HOME_KEY);
        ResourceLocation id = home.isEmpty() ? null : ResourceLocation.tryParse(home);
        if (id != null && FealtyWorldData.get(level.getServer()).village(id).isPresent()) {
            return id;
        }
        return nearestVillage(level, captive.blockPosition()).map(VillageRecord::id).orElse(null);
    }

    /**
     * Free a captive: they thank whoever freed them and slip away home.
     *
     * @param rescuer the player who freed them (they earn the home village's thanks), or null when a raid did
     */
    public static void free(ServerLevel level, Villager captive, @Nullable ServerPlayer rescuer) {
        if (!isCaptive(captive)) {
            return;
        }
        captive.removeTag(CAPTIVE_TAG);
        captive.addTag(FREED_TAG);
        BlockPos camp = BlockPos.of(captive.getPersistentData().getLong(CAMP_KEY));
        ResourceLocation home = home(level, captive);
        NeoForge.EVENT_BUS.post(new CaptiveEvent.Freed(captive, camp, home, rescuer));
        if (Speech.bark(captive, "captive_freed", rescuer, 0).isEmpty() && rescuer != null) {
            rescuer.displayClientMessage(Component.translatable("fealty.captive.freed", captive.getDisplayName()), true);
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, captive.getX(), captive.getY() + 1.2, captive.getZ(), 8, 0.4, 0.5, 0.4, 0.0);
        level.playSound(null, captive.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        LEAVING.add(new Departure(level.dimension(), captive.getUUID(), level.getGameTime() + LEAVE_TICKS, home));
        Strongholds strongholds = Strongholds.get(level.getServer());
        Strongholds.Entry entry = strongholds.at(level.dimension(), camp, 2);
        if (entry != null) {
            entry.setCaptives(entry.captives() - 1);
            strongholds.setDirty();
        }
        if (rescuer != null) {
            RepManager.data(rescuer).addStat("captives_freed", 1);
            if (home != null) {
                RepManager.meet(rescuer, home);
                RepManager.applySource(rescuer, home, RepSources.FREE_CAPTIVE);
            }
            FealtyEvents.fire(rescuer, FealtyEvents.CAPTIVE_FREED);
            for (QuestContext ctx : QuestManager.activeContexts(rescuer)) {
                if (ctx.definition().objective() instanceof FreeCaptivesObjective objective) {
                    objective.onFreed(ctx);
                }
            }
            if (RepManager.data(rescuer).stat("captives_freed") >= 10) {
                FealtyEvents.fire(rescuer, FealtyEvents.LIBERATOR);
            }
        }
    }

    /** Breaking a cage's bars frees whoever is inside. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.getState().is(Blocks.IRON_BARS) || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        for (Villager captive : near(level, event.getPos(), 4)) {
            free(level, captive, player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (LEAVING.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }
        Iterator<Departure> it = LEAVING.iterator();
        while (it.hasNext()) {
            Departure departure = it.next();
            ServerLevel level = server.getLevel(departure.dimension());
            if (level == null) {
                it.remove();
                continue;
            }
            if (level.getGameTime() < departure.due()) {
                continue;
            }
            it.remove();
            Entity entity = level.getEntity(departure.entity());
            if (entity != null && entity.isAlive()) {
                level.sendParticles(ParticleTypes.POOF, entity.getX(), entity.getY() + 0.8, entity.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                entity.discard();
            }
            if (departure.home() != null) {
                FealtyWorldData data = FealtyWorldData.get(server);
                data.village(departure.home()).ifPresent(village -> {
                    village.sites().setIncomingVillagers(village.sites().incomingVillagers() + 1);
                    data.setDirty();
                });
            }
        }
    }

    /** Freed villagers on their way home arrive while someone is in the village. */
    public static void tickVillage(ServerLevel level, VillageRecord village) {
        int coming = village.sites().incomingVillagers();
        BlockPos center = village.center();
        if (coming <= 0 || !village.dimension().equals(level.dimension()) || !level.isLoaded(center)
                || !level.areEntitiesLoaded(ChunkPos.asLong(center))) {
            return;
        }
        RandomSource random = level.getRandom();
        int arrived = 0;
        for (int i = 0; i < coming; i++) {
            Optional<BlockPos> spot = SpawnSpots.nearY(level, center.getX() + random.nextInt(13) - 6, center.getZ() + random.nextInt(13) - 6,
                    center.getY(), 8);
            Villager villager = spot.isPresent() ? EntityType.VILLAGER.create(level) : null;
            if (villager == null) {
                continue;
            }
            BlockPos pos = spot.get();
            villager.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360F, 0);
            villager.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            level.addFreshEntity(villager);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.2, villager.getZ(), 6, 0.4, 0.5, 0.4, 0.0);
            Speech.bark(villager, "home_again", null, 0);
            arrived++;
        }
        if (arrived == 0) {
            return;
        }
        village.sites().setIncomingVillagers(coming - arrived);
        FealtyWorldData.get(level.getServer()).setDirty();
        NeoForge.EVENT_BUS.post(new VillageEvent(level.getServer(), village.id(), VillageEvent.Type.VILLAGERS_RETURNED));
        Component message = Component.translatable("fealty.captive.home", arrived, village.name());
        for (ServerPlayer player : level.players()) {
            if (village.contains(level.dimension(), player.blockPosition())) {
                player.displayClientMessage(message, true);
            }
        }
        UUID lord = village.lord().uuid();
        ServerPlayer online = lord != null ? level.getServer().getPlayerList().getPlayer(lord) : null;
        if (online != null) {
            Feedback.toast(online, "house", Component.translatable("fealty.toast.captives_home"), message);
        }
    }

    /** The nearest village Fealty knows of within reach of a place. */
    public static Optional<VillageRecord> nearestVillage(ServerLevel level, BlockPos pos) {
        VillageRecord best = null;
        double bestDistance = HOME_RANGE * HOME_RANGE;
        for (VillageRecord village : FealtyWorldData.get(level.getServer()).villages()) {
            if (!village.dimension().equals(level.dimension())) {
                continue;
            }
            double distance = village.center().distSqr(pos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = village;
            }
        }
        return Optional.ofNullable(best);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LEAVING.clear();
    }
}
