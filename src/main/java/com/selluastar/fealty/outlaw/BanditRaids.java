package com.selluastar.fealty.outlaw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.api.event.BanditRaidEvent;
import com.selluastar.fealty.block.BanditStandardBlockEntity;
import com.selluastar.fealty.block.VillageCofferBlockEntity;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.entity.BanditEntity;
import com.selluastar.fealty.guard.GuardManager;
import com.selluastar.fealty.mixin.MobAccessor;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageTracker;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Bandit raids. Every few days a manned camp may send a raiding party against a village within reach while a player
 * is there: the bell rings, a bar shows how many raiders are left, and the bandits come over the hill for the
 * guards, the villagers and the coffer. Everyone who stands with the village when the last raider falls earns its
 * thanks; raiders who reach the coffer carry off what they can, and drop it when they die. If the raid is not beaten
 * in time, the bandits make off to their camp with whatever they took.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class BanditRaids {
    private static final int CHECK_INTERVAL = 1200;
    /** Once a village is due a raid, the chance of one each minute a player is there. */
    private static final int RAID_CHANCE = 12;
    private static final int RAID_TIMEOUT = 6000;
    private static final double DEFENDER_RANGE = 96;
    private static final String RAIDER_TAG = "fealty.raider";
    private static final Map<ResourceLocation, Raid> RAIDS = new HashMap<>();

    private BanditRaids() {
    }

    private static final class Raid {
        final ResourceLocation village;
        final ResourceKey<Level> dimension;
        final BlockPos center;
        @Nullable
        final BlockPos camp;
        final long started;
        final ServerBossEvent bar;
        final Set<UUID> alive = new HashSet<>();
        final Set<UUID> defenders = new HashSet<>();
        final Map<UUID, List<ItemStack>> plunder = new HashMap<>();
        int total;
        long lastDefender;
        boolean lootingAnnounced;

        Raid(VillageRecord village, @Nullable BlockPos camp, long now) {
            this.village = village.id();
            this.dimension = village.dimension();
            this.center = village.center();
            this.camp = camp;
            this.started = now;
            this.lastDefender = now;
            this.bar = new ServerBossEvent(Component.translatable("fealty.raid.bar", village.name()), BossEvent.BossBarColor.RED,
                    BossEvent.BossBarOverlay.NOTCHED_10);
        }
    }

    public static boolean isRaiding(ResourceLocation village) {
        return RAIDS.containsKey(village);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int ticks = server.getTickCount();
        if (ticks % 20 == 0 && !RAIDS.isEmpty()) {
            tickRaids(server);
        }
        if (ticks % CHECK_INTERVAL == 0 && FealtyConfig.BANDIT_RAIDS.get()) {
            maybeStart(server);
        }
    }

    /** Villages with a player in them, due a raid, with a manned camp in reach, may be raided. */
    private static void maybeStart(MinecraftServer server) {
        long day = RepManager.day(server);
        FealtyWorldData world = FealtyWorldData.get(server);
        Set<ResourceLocation> considered = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            Optional<VillageRecord> village = VillageTracker.currentVillage(player).flatMap(world::village);
            if (village.isEmpty() || RAIDS.containsKey(village.get().id()) || !considered.add(village.get().id())) {
                continue;
            }
            VillageRecord record = village.get();
            if (record.sites().lastRaidDay() < 0) {
                // A village seen for the first time is left in peace for a few days.
                record.sites().setLastRaidDay(day);
                world.setDirty();
                continue;
            }
            int min = FealtyConfig.RAID_MIN_DAYS.get();
            int spread = Math.max(1, FealtyConfig.RAID_MAX_DAYS.get() - min + 1);
            long gap = min + Math.floorMod(record.id().hashCode() + record.sites().lastRaidDay(), spread);
            ServerLevel level = player.serverLevel();
            if (day - record.sites().lastRaidDay() < gap || record.sites().atPeace(day) || !level.dimension().equals(record.dimension())
                    || level.getRandom().nextInt(RAID_CHANCE) != 0) {
                continue;
            }
            BanditCamps.get(server).nearestActive(level, record.center(), FealtyConfig.RAID_RANGE.get())
                    .ifPresent(camp -> start(level, record, camp));
        }
    }

    /**
     * Send a raiding party against a village.
     *
     * @param camp the camp they come from (they come from its direction and flee back to it), or null
     * @return how many raiders set out
     */
    public static int start(ServerLevel level, VillageRecord village, @Nullable BlockPos camp) {
        if (RAIDS.containsKey(village.id())) {
            return 0;
        }
        long day = RepManager.day(level.getServer());
        RandomSource random = level.getRandom();
        int villagers = level.getEntitiesOfClass(AbstractVillager.class, new AABB(village.center()).inflate(48),
                v -> !(v instanceof WanderingTrader)).size();
        int size = 3 + Math.min(3, villagers / 6) + (day >= 20 ? 1 : 0);
        BanditRaidEvent.Start start = NeoForge.EVENT_BUS.post(new BanditRaidEvent.Start(level, village.id(), camp, size));
        if (start.isCanceled()) {
            return 0;
        }
        size = start.getSize();
        BlockPos center = village.center();
        double toward = camp != null ? Math.atan2(camp.getZ() - center.getZ(), camp.getX() - center.getX()) : random.nextDouble() * Math.PI * 2;
        Raid raid = new Raid(village, camp, level.getGameTime());
        for (int i = 0; i < size; i++) {
            BanditEntity bandit = ModEntities.BANDIT.get().create(level);
            if (bandit == null) {
                continue;
            }
            AttributeInstance range = bandit.getAttribute(Attributes.FOLLOW_RANGE);
            if (range != null) {
                range.setBaseValue(64);
            }
            Optional<BlockPos> spot = approach(level, center, bandit, random, toward);
            if (spot.isEmpty()) {
                bandit.discard();
                continue;
            }
            BlockPos pos = spot.get();
            bandit.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360F, 0);
            bandit.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
            bandit.setPersistenceRequired();
            bandit.addTag(RAIDER_TAG);
            if (camp != null) {
                // Should the raid be cut short (the server stops), they go home.
                bandit.setData(ModAttachments.CAMP, camp.asLong());
            }
            bandit.restrictTo(center, 20);
            MobAccessor goals = (MobAccessor) bandit;
            goals.fealty$getTargetSelector().addGoal(4, new NearestAttackableTargetGoal<>(bandit, Mob.class, 10, true, false,
                    m -> m instanceof Mob mob && GuardManager.isGuard(mob)));
            goals.fealty$getTargetSelector().addGoal(5, new NearestAttackableTargetGoal<>(bandit, AbstractVillager.class, 10, true, false,
                    v -> !(v instanceof WanderingTrader)));
            level.addFreshEntity(bandit);
            raid.alive.add(bandit.getUUID());
        }
        raid.total = raid.alive.size();
        if (raid.total == 0) {
            return 0;
        }
        RAIDS.put(village.id(), raid);
        village.sites().setLastRaidDay(day);
        FealtyWorldData.get(level.getServer()).setDirty();
        Optional<BlockPos> bell = level.getPoiManager().findClosest(h -> h.is(PoiTypes.MEETING), center, 48, PoiManager.Occupancy.ANY);
        if (bell.isPresent() && level.getBlockState(bell.get()).getBlock() instanceof BellBlock block) {
            block.attemptToRing(level, bell.get(), null);
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(Vec3.atCenterOf(center)) <= DEFENDER_RANGE * DEFENDER_RANGE) {
                Feedback.banner(player, Component.translatable("fealty.raid.start"), Component.translatable("fealty.raid.start.detail", village.name()),
                        0xC0392B, "sword");
                Feedback.sound(player, SoundEvents.RAID_HORN, 1.0F, 1.0F);
                raid.bar.addPlayer(player);
            }
        }
        return raid.total;
    }

    /** Open ground 40 to 60 blocks out, roughly from the camp's direction, from where the raider can walk in. */
    private static Optional<BlockPos> approach(ServerLevel level, BlockPos center, Mob probe, RandomSource random, double toward) {
        for (int tries = 0; tries < 24; tries++) {
            double angle = toward + (random.nextDouble() - 0.5) * (tries < 12 ? Math.PI / 2 : Math.PI * 2);
            double distance = 40 + random.nextDouble() * 20;
            int x = Mth.floor(center.getX() + 0.5 + Math.cos(angle) * distance);
            int z = Mth.floor(center.getZ() + 0.5 + Math.sin(angle) * distance);
            Optional<BlockPos> spot = com.selluastar.fealty.util.SpawnSpots.surface(level, x, z);
            if (spot.isPresent() && BanditStandardBlockEntity.canWalk(probe, spot.get(), center)) {
                return spot;
            }
        }
        return Optional.empty();
    }

    private static void tickRaids(MinecraftServer server) {
        Iterator<Map.Entry<ResourceLocation, Raid>> it = RAIDS.entrySet().iterator();
        while (it.hasNext()) {
            Raid raid = it.next().getValue();
            ServerLevel level = server.getLevel(raid.dimension);
            Optional<VillageRecord> village = FealtyWorldData.get(server).village(raid.village);
            if (level == null || village.isEmpty()) {
                raid.bar.removeAllPlayers();
                it.remove();
                continue;
            }
            long now = level.getGameTime();
            Vec3 center = Vec3.atCenterOf(raid.center);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                boolean near = player.level() == level && player.distanceToSqr(center) <= DEFENDER_RANGE * DEFENDER_RANGE;
                if (near && !player.isSpectator()) {
                    raid.bar.addPlayer(player);
                    if (!player.isCreative()) {
                        raid.defenders.add(player.getUUID());
                        raid.lastDefender = now;
                    }
                } else {
                    raid.bar.removePlayer(player);
                }
            }
            loot(level, raid, village.get());
            raid.bar.setProgress(raid.total == 0 ? 0F : (float) raid.alive.size() / raid.total);
            if (raid.alive.isEmpty()) {
                won(server, raid, village.get());
                it.remove();
            } else if (now - raid.started > RAID_TIMEOUT || now - raid.lastDefender > 1200) {
                withdraw(level, raid, village.get());
                it.remove();
            }
        }
    }

    /** Raiders at the coffer help themselves. */
    private static void loot(ServerLevel level, Raid raid, VillageRecord village) {
        BlockPos coffer = village.elder().coffer();
        if (coffer == null || !level.isLoaded(coffer) || !(level.getBlockEntity(coffer) instanceof VillageCofferBlockEntity box)) {
            return;
        }
        for (UUID id : raid.alive) {
            Entity raider = level.getEntity(id);
            if (raider == null || raider.distanceToSqr(Vec3.atCenterOf(coffer)) > 9 || level.getRandom().nextInt(5) != 0) {
                continue;
            }
            for (int slot = 0; slot < box.getContainerSize(); slot++) {
                ItemStack stack = box.getItem(slot);
                if (!stack.isEmpty()) {
                    raid.plunder.computeIfAbsent(id, k -> new ArrayList<>()).add(box.removeItem(slot, Math.min(4, stack.getCount())));
                    break;
                }
            }
            if (!raid.lootingAnnounced) {
                raid.lootingAnnounced = true;
                for (UUID defender : raid.defenders) {
                    ServerPlayer player = level.getServer().getPlayerList().getPlayer(defender);
                    if (player != null) {
                        player.displayClientMessage(Component.translatable("fealty.raid.looting", village.name()).withStyle(ChatFormatting.RED), true);
                    }
                }
            }
        }
    }

    private static void won(MinecraftServer server, Raid raid, VillageRecord village) {
        raid.bar.removeAllPlayers();
        List<ServerPlayer> defenders = new ArrayList<>();
        for (UUID id : raid.defenders) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                defenders.add(player);
            }
        }
        ServerLevel level = server.getLevel(raid.dimension);
        if (level != null) {
            NeoForge.EVENT_BUS.post(new BanditRaidEvent.Won(level, village.id(), defenders));
        }
        for (UUID id : raid.defenders) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            RepManager.meet(player, village.id());
            RepManager.applySource(player, village.id(), RepSources.DEFEND_BANDIT_RAID);
            FealtyEvents.fire(player, FealtyEvents.RAID_DEFENDED);
            Feedback.banner(player, Component.translatable("fealty.raid.won"), Component.translatable("fealty.raid.won.detail", village.name()),
                    0xD4AF37, "shield");
            Feedback.sound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.0F);
        }
    }

    /** Time is up: the raiders make off with what they took, back to their camp's stores. */
    private static void withdraw(ServerLevel level, Raid raid, VillageRecord village) {
        raid.bar.removeAllPlayers();
        List<ItemStack> taken = new ArrayList<>();
        for (UUID id : raid.alive) {
            List<ItemStack> carried = raid.plunder.remove(id);
            if (carried != null) {
                taken.addAll(carried);
            }
            Entity raider = level.getEntity(id);
            if (raider != null) {
                level.sendParticles(ParticleTypes.POOF, raider.getX(), raider.getY() + 0.8, raider.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                raider.discard();
            }
        }
        NeoForge.EVENT_BUS.post(new BanditRaidEvent.Withdrew(level, village.id(), taken));
        if (!taken.isEmpty() && raid.camp != null) {
            stash(level, raid.camp, taken);
        }
        for (UUID id : raid.defenders) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendSystemMessage(Component.translatable(taken.isEmpty() ? "fealty.raid.withdrew" : "fealty.raid.withdrew_loot", village.name())
                        .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /** Put plunder in a chest or barrel at the camp, if the camp is loaded. */
    private static void stash(ServerLevel level, BlockPos camp, List<ItemStack> items) {
        if (!level.isLoaded(camp)) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(camp.offset(-14, -3, -14), camp.offset(14, 6, 14))) {
            if (level.getBlockEntity(pos) instanceof Container container) {
                for (ItemStack stack : items) {
                    for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
                        if (container.getItem(slot).isEmpty()) {
                            container.setItem(slot, stack.copyAndClear());
                        }
                    }
                }
                container.setChanged();
                return;
            }
        }
    }

    /** A raider falls: one fewer, and whatever it carried drops where it fell. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (RAIDS.isEmpty() || victim.level().isClientSide || !victim.getTags().contains(RAIDER_TAG)) {
            return;
        }
        for (Raid raid : RAIDS.values()) {
            if (raid.alive.remove(victim.getUUID())) {
                List<ItemStack> carried = raid.plunder.remove(victim.getUUID());
                if (carried != null) {
                    carried.forEach(victim::spawnAtLocation);
                }
                if (event.getSource().getEntity() instanceof ServerPlayer killer && !killer.isCreative()) {
                    raid.defenders.add(killer.getUUID());
                }
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        RAIDS.values().forEach(raid -> raid.bar.removeAllPlayers());
        RAIDS.clear();
    }
}
