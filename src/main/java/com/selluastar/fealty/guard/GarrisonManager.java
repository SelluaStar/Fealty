package com.selluastar.fealty.guard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.entity.VillageGuardEntity;
import com.selluastar.fealty.item.LordsHornItem;
import com.selluastar.fealty.network.FealtyNetwork;
import com.selluastar.fealty.network.Feedback;
import com.selluastar.fealty.network.OpenHornPayload;
import com.selluastar.fealty.network.RetinuePayload;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.registry.ModDataComponents;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.rep.FactionResolver;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.util.SpawnSpots;
import com.selluastar.fealty.village.SitePlanner;
import com.selluastar.fealty.village.VillageNames;
import com.selluastar.fealty.village.VillageRecord;
import com.selluastar.fealty.village.VillageResolver;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps each village's watch: swears in new guards when the village is visited (a fallen guard's place is filled
 * after {@code guard_respawn_days}), answers the Lord's Horn wherever the lord is (guards nearby fall in, and more
 * arrive from out of sight a few seconds later), sends guards home, and tells lords which guards follow them.
 */
@EventBusSubscriber(modid = Fealty.MOD_ID)
public final class GarrisonManager {
    /** Guards already this close to the lord fall in on foot rather than being sent for. */
    private static final double FALL_IN_RANGE = 96.0;
    private static final Map<UUID, List<Arrival>> ARRIVALS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_RETINUE = new HashMap<>();

    /** A guard sent for by a lord, due to arrive at {@code due} (game time). */
    private record Arrival(ResourceLocation village, int slot, int generation, long due, int attempts) {
    }

    private GarrisonManager() {
    }

    // ---- Upkeep ----

    /** How many guards a village of this size keeps (not counting the sergeant). */
    public static int wanted(int population) {
        if (population <= 0) {
            return FealtyConfig.EMPTY_VILLAGE_GUARDS.get();
        }
        int count = Math.round(population / (float) FealtyConfig.VILLAGERS_PER_GUARD.get());
        return Mth.clamp(count, FealtyConfig.MIN_GUARDS.get(), Math.max(FealtyConfig.MIN_GUARDS.get(), FealtyConfig.MAX_GUARDS.get()));
    }

    /** Add posts as the village grows: swordsmen and archers in turn, and a sergeant for a big village. */
    private static boolean grow(Garrison garrison, int population) {
        boolean changed = false;
        int regulars = 0;
        boolean sergeant = false;
        for (Garrison.Slot slot : garrison.slots()) {
            if (slot.rank() == Garrison.Rank.SERGEANT) {
                sergeant = true;
            } else {
                regulars++;
            }
        }
        for (int i = regulars; i < wanted(population); i++) {
            garrison.slots().add(new Garrison.Slot(i % 2 == 0 ? Garrison.Rank.SWORDSMAN : Garrison.Rank.ARCHER));
            changed = true;
        }
        int sergeantAt = FealtyConfig.SERGEANT_POPULATION.get();
        if (!sergeant && sergeantAt > 0 && population >= sergeantAt) {
            garrison.slots().add(new Garrison.Slot(Garrison.Rank.SERGEANT));
            changed = true;
        }
        return changed;
    }

    /** Where the watch musters: the village's guard post, found once, else its centre. */
    public static BlockPos post(ServerLevel level, VillageRecord record) {
        BlockPos post = record.sites().guardPost();
        if (post == null && level.isLoaded(record.center())) {
            post = SitePlanner.outdoors(level, record, record.center(), 12).orElse(null);
            if (post != null) {
                record.sites().setGuardPost(post);
                FealtyWorldData.get(level.getServer()).setDirty();
            }
        }
        return post != null ? post : record.center();
    }

    /** Called every few seconds while a player is in the village: count the villagers and fill empty posts. */
    public static void tickVillage(ServerLevel level, VillageRecord record) {
        if (!record.dimension().equals(level.dimension())) {
            return;
        }
        if (noteOthers(level, record)) {
            FealtyWorldData.get(level.getServer()).setDirty();
        }
        if (!FealtyConfig.FEALTY_GUARDS.get()) {
            return;
        }
        BlockPos post = post(level, record);
        if (!level.isLoaded(post) || !level.areEntitiesLoaded(ChunkPos.asLong(post))) {
            return;
        }
        Garrison garrison = record.garrison();
        boolean changed = false;
        int population = level.getEntitiesOfClass(Villager.class, AABB.of(record.bounds()),
                v -> v.isAlive() && record.id().equals(FactionResolver.factionOf(v).orElse(null))).size();
        if (population != garrison.population()) {
            garrison.setPopulation(population);
            changed = true;
        }
        changed |= grow(garrison, population);
        long day = RepManager.day(level.getServer());
        for (int i = 0; i < garrison.slots().size(); i++) {
            Garrison.Slot slot = garrison.slots().get(i);
            if (slot.entity() != null) {
                Entity holder = level.getEntity(slot.entity());
                if (holder instanceof VillageGuardEntity && holder.isAlive()) {
                    slot.missing = 0;
                } else if (level.dimension().equals(slot.lastDimension()) && slot.lastPos() != null && level.isLoaded(slot.lastPos())
                        && level.areEntitiesLoaded(ChunkPos.asLong(slot.lastPos())) && ++slot.missing >= 3) {
                    // Their last known place is loaded and they are not there: lost (removed by a command, say).
                    slot.vacate();
                    changed = true;
                }
                continue;
            }
            if (isPending(record.id(), i) || (slot.isDead() && day - slot.diedDay() < FealtyConfig.GUARD_RESPAWN_DAYS.get())) {
                continue;
            }
            boolean replacement = slot.isDead();
            VillageGuardEntity guard = spawn(level, record, i, slot, post, post, 6);
            if (guard != null) {
                changed = true;
                if (replacement) {
                    tellLord(level.getServer(), record, "guard", Component.translatable("fealty.toast.guard_sworn"),
                            Component.translatable("fealty.toast.guard_sworn.detail", guard.getDisplayName(), record.name()));
                }
            }
        }
        if (changed) {
            FealtyWorldData.get(level.getServer()).setDirty();
        }
    }

    /** Make a new holder for a slot near a spot. */
    @Nullable
    private static VillageGuardEntity spawn(ServerLevel level, VillageRecord record, int index, Garrison.Slot slot, BlockPos post,
                                            BlockPos near, int spread) {
        Optional<BlockPos> spot = Optional.empty();
        RandomSource random = level.getRandom();
        for (int tries = 0; tries < 16 && spot.isEmpty(); tries++) {
            int x = near.getX() + random.nextInt(spread * 2 + 1) - spread;
            int z = near.getZ() + random.nextInt(spread * 2 + 1) - spread;
            spot = SpawnSpots.nearY(level, x, z, near.getY(), 6);
        }
        if (spot.isEmpty()) {
            return null;
        }
        VillageGuardEntity guard = ModEntities.VILLAGE_GUARD.get().create(level);
        if (guard == null) {
            return null;
        }
        BlockPos pos = spot.get();
        guard.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
        String name = slot.name().isEmpty() ? VillageNames.personName(random) : slot.name();
        guard.enlist(record, index, slot, post, name);
        if (!level.addFreshEntity(guard)) {
            return null;
        }
        slot.fill(guard.getUUID(), name, level.dimension(), pos);
        return guard;
    }

    /**
     * A guard checks in every couple of seconds: one whose slot has a newer holder (or whose village is gone) leaves
     * the world. @return whether the guard is still on the roster
     */
    public static boolean checkHolder(ServerLevel level, VillageGuardEntity guard) {
        if (guard.village() == null || guard.slot() < 0) {
            return true;
        }
        FealtyWorldData data = FealtyWorldData.get(level.getServer());
        Optional<VillageRecord> record = data.village(guard.village());
        Optional<Garrison.Slot> slot = record.flatMap(r -> r.garrison().slot(guard.slot()));
        if (!FealtyConfig.FEALTY_GUARDS.get() || slot.isEmpty() || !guard.getUUID().equals(slot.get().entity())
                || slot.get().generation() != guard.generation()) {
            guard.discard();
            return false;
        }
        slot.get().seen(level.dimension(), guard.blockPosition());
        data.setDirty();
        return true;
    }

    public static boolean inHomeDimension(ServerLevel level, VillageGuardEntity guard) {
        return guard.village() == null || FealtyWorldData.get(level.getServer()).village(guard.village())
                .map(r -> r.dimension().equals(level.dimension())).orElse(true);
    }

    /** The guard leaves (out of sight, or far from home); a new holder takes their post when the village is next visited. */
    public static void sendHome(ServerLevel level, VillageGuardEntity guard) {
        if (guard.village() != null && guard.slot() >= 0) {
            FealtyWorldData data = FealtyWorldData.get(level.getServer());
            data.village(guard.village()).flatMap(r -> r.garrison().slot(guard.slot())).ifPresent(slot -> {
                if (guard.getUUID().equals(slot.entity())) {
                    slot.vacate();
                }
            });
            data.setDirty();
        }
        guard.discard();
    }

    public static void onDied(ServerLevel level, VillageGuardEntity guard, DamageSource source) {
        if (guard.village() == null || guard.slot() < 0) {
            return;
        }
        MinecraftServer server = level.getServer();
        Optional<VillageRecord> record = FealtyWorldData.get(server).village(guard.village());
        Optional<Garrison.Slot> slot = record.flatMap(r -> r.garrison().slot(guard.slot()));
        if (slot.isEmpty() || !guard.getUUID().equals(slot.get().entity())) {
            return;
        }
        slot.get().died(RepManager.day(server));
        FealtyWorldData.get(server).setDirty();
        tellLord(server, record.get(), "skull", Component.translatable("fealty.toast.guard_fell"),
                Component.translatable("fealty.toast.guard_fell.detail", guard.getDisplayName(), record.get().name(),
                        FealtyConfig.GUARD_RESPAWN_DAYS.get()));
    }

    /**
     * Keep the roster of the village's other guards (iron golems, Guard Villagers' guards, ...) up to date from those
     * in and around the village now. @return whether it changed
     */
    private static boolean noteOthers(ServerLevel level, VillageRecord record) {
        long day = RepManager.day(level.getServer());
        Garrison garrison = record.garrison();
        boolean changed = false;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, AABB.of(record.bounds()).inflate(16),
                m -> !(m instanceof VillageGuardEntity) && isVillageGuard(m, record))) {
            changed |= garrison.noteOther(mob.getUUID(), otherKind(mob), mob.hasCustomName() ? mob.getCustomName().getString() : "", day);
        }
        return garrison.forgetOthers(day) | changed;
    }

    /** {@code golem} for iron golems, {@code guard} for other mods' guards. */
    public static String otherKind(Mob mob) {
        return mob instanceof IronGolem ? "golem" : "guard";
    }

    /** One of the village's other guards fell: the lord hears of it, and the roster shows it. */
    @SubscribeEvent
    public static void onOtherGuardDied(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob instanceof VillageGuardEntity || !(mob.level() instanceof ServerLevel level)
                || !GuardManager.isGuard(mob)) {
            return;
        }
        MinecraftServer server = level.getServer();
        Optional<VillageRecord> record = FactionResolver.factionOf(mob).flatMap(id -> FealtyWorldData.get(server).village(id));
        if (record.isPresent() && record.get().garrison().otherDied(mob.getUUID(), RepManager.day(server))) {
            FealtyWorldData.get(server).setDirty();
            tellLord(server, record.get(), "skull", Component.translatable("fealty.toast.guard_fell"),
                    Component.translatable("fealty.toast.other_guard_fell.detail", mob.getDisplayName(), record.get().name()));
        }
    }

    private static void tellLord(MinecraftServer server, VillageRecord record, String icon, Component title, Component detail) {
        UUID lord = record.lord().uuid();
        ServerPlayer player = lord != null ? server.getPlayerList().getPlayer(lord) : null;
        if (player != null) {
            Feedback.toast(player, icon, title, detail);
        }
    }

    /** A guard from a spawn egg joins the village it is placed in, without a place on the roster. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof VillageGuardEntity guard
                && guard.village() == null && !guard.hasData(ModAttachments.FACTION) && !event.loadedFromDisk()) {
            VillageResolver.villageAt(level, guard.blockPosition()).ifPresent(guard::joinVillage);
        }
    }

    // ---- The Lord's Horn ----

    /** Whether a mob is one of this village's guards (Fealty's, golems, or another mod's). */
    private static boolean isVillageGuard(Mob mob, VillageRecord record) {
        return mob.isAlive() && GuardManager.isGuard(mob) && record.id().equals(FactionResolver.factionOf(mob).orElse(null));
    }

    /** The village a horn speaks for: the one picked on the horn, else the one the lord stands in, else their only one. */
    public static Optional<VillageRecord> hornVillage(ServerPlayer player, ItemStack horn) {
        FealtyWorldData data = FealtyWorldData.get(player.server);
        ResourceLocation picked = horn.get(ModDataComponents.HORN_VILLAGE.get());
        if (picked != null) {
            Optional<VillageRecord> village = data.village(picked);
            if (village.isPresent() && village.get().lord().isLord(player.getUUID())) {
                return village;
            }
        }
        Optional<VillageRecord> here = VillageResolver.villageAt(player.serverLevel(), player.blockPosition());
        if (here.isPresent() && here.get().lord().isLord(player.getUUID())) {
            return here;
        }
        return data.villages().stream().filter(v -> v.lord().isLord(player.getUUID())).findFirst();
    }

    /** Sound the horn with an order. */
    public static void blow(ServerPlayer player, ItemStack horn, HornOrder order) {
        Optional<VillageRecord> village = hornVillage(player, horn);
        if (village.isEmpty()) {
            player.displayClientMessage(Component.translatable("fealty.horn.no_village"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value(), SoundSource.PLAYERS, 6.0F, 1.0F);
        switch (order) {
            case CALL -> call(player, village.get());
            case HOLD, GUARD -> station(player, village.get(), order);
            case RETURN -> dismiss(player, village.get());
        }
        FealtyEvents.fire(player, FealtyEvents.GUARDS_COMMANDED);
    }

    /** Guards near the lord fall in behind them; more of the watch are sent for, wherever the lord is. */
    private static void call(ServerPlayer lord, VillageRecord record) {
        ServerLevel level = lord.serverLevel();
        long now = level.getGameTime();
        List<Mob> near = level.getEntitiesOfClass(Mob.class, lord.getBoundingBox().inflate(FALL_IN_RANGE), m -> isVillageGuard(m, record));
        near.sort((a, b) -> Double.compare(a.distanceToSqr(lord), b.distanceToSqr(lord)));
        int commanded = 0;
        int ownGuards = 0;
        for (Mob guard : near) {
            if (commanded >= FealtyConfig.MAX_COMMANDED_GUARDS.get()) {
                break;
            }
            guard.getData(ModAttachments.GUARD_ORDERS).set(GuardOrders.Mode.FOLLOW, lord.getUUID(), 0, null);
            guard.setTarget(null);
            commanded++;
            if (guard instanceof VillageGuardEntity) {
                ownGuards++;
            }
        }
        int sent = 0;
        int need = Math.min(FealtyConfig.HORN_SUMMON_COUNT.get() - ownGuards, FealtyConfig.MAX_COMMANDED_GUARDS.get() - commanded);
        if (FealtyConfig.FEALTY_GUARDS.get() && need > 0) {
            Garrison garrison = record.garrison();
            int delay = FealtyConfig.HORN_ARRIVAL_SECONDS.get() * 20;
            for (int i = 0; i < garrison.slots().size() && sent < need; i++) {
                Garrison.Slot slot = garrison.slots().get(i);
                if (slot.isDead() || isPending(record.id(), i)) {
                    continue;
                }
                Entity holder = holder(lord.server, slot);
                if (holder != null && near.contains(holder)) {
                    continue;
                }
                if (holder != null) {
                    holder.discard(); // sets off from the village, out of the lord's sight
                }
                slot.vacate();
                ARRIVALS.computeIfAbsent(lord.getUUID(), k -> new ArrayList<>())
                        .add(new Arrival(record.id(), i, slot.generation(), now + delay + sent * 10L, 0));
                sent++;
            }
            if (sent > 0) {
                FealtyWorldData.get(lord.server).setDirty();
            }
        }
        if (commanded + sent == 0) {
            lord.displayClientMessage(Component.translatable("fealty.horn.none_answer", record.name()), true);
        } else if (sent > 0) {
            lord.displayClientMessage(Component.translatable("fealty.horn.called", commanded, sent, record.name()), true);
        } else {
            lord.displayClientMessage(Component.translatable("fealty.horn.ordered.follow", commanded), true);
        }
        syncRetinue(lord, true);
    }

    /** The lord's guards hold where they stand, or keep watch over the area around the lord. */
    private static void station(ServerPlayer lord, VillageRecord record, HornOrder order) {
        List<Mob> retinue = retinue(lord, record);
        for (Mob guard : retinue) {
            GuardOrders orders = guard.getData(ModAttachments.GUARD_ORDERS);
            if (order == HornOrder.HOLD) {
                orders.set(GuardOrders.Mode.HOLD, lord.getUUID(), 0, guard.blockPosition());
            } else {
                orders.set(GuardOrders.Mode.GUARD, lord.getUUID(), 0, lord.blockPosition());
            }
        }
        lord.displayClientMessage(retinue.isEmpty() ? Component.translatable("fealty.horn.no_retinue")
                : Component.translatable("fealty.horn.ordered." + order.id(), retinue.size()), true);
        syncRetinue(lord, true);
    }

    /** The lord's guards go home: Fealty guards walk off and return to their posts, others walk back to the village. */
    private static void dismiss(ServerPlayer lord, VillageRecord record) {
        List<Mob> retinue = retinue(lord, record);
        for (Mob guard : retinue) {
            GuardOrders orders = guard.getData(ModAttachments.GUARD_ORDERS);
            if (guard instanceof VillageGuardEntity) {
                orders.set(GuardOrders.Mode.RETURN, lord.getUUID(), 0, null);
            } else if (guard.level().dimension().equals(record.dimension())) {
                // Other guards walk back to the village's post (and, once nobody sees them, make the rest of the way home).
                orders.set(GuardOrders.Mode.RETURN, lord.getUUID(), 0, post(lord.serverLevel(), record));
            } else {
                orders.clear();
            }
        }
        ARRIVALS.computeIfPresent(lord.getUUID(), (k, list) -> {
            list.removeIf(a -> a.village().equals(record.id()));
            return list.isEmpty() ? null : list;
        });
        lord.displayClientMessage(retinue.isEmpty() ? Component.translatable("fealty.horn.no_retinue")
                : Component.translatable("fealty.horn.ordered.return", retinue.size()), true);
        syncRetinue(lord, true);
    }

    /** The guards of a village under this lord's orders near them. */
    private static List<Mob> retinue(ServerPlayer lord, VillageRecord record) {
        long now = lord.level().getGameTime();
        return lord.serverLevel().getEntitiesOfClass(Mob.class, lord.getBoundingBox().inflate(FALL_IN_RANGE), m -> {
            if (!isVillageGuard(m, record) || !m.hasData(ModAttachments.GUARD_ORDERS)) {
                return false;
            }
            GuardOrders orders = m.getData(ModAttachments.GUARD_ORDERS);
            return orders.isActive(now) && orders.isLordsOrder() && lord.getUUID().equals(orders.leader());
        });
    }

    @Nullable
    private static Entity holder(MinecraftServer server, Garrison.Slot slot) {
        if (slot.entity() == null || slot.lastDimension() == null) {
            return null;
        }
        ServerLevel level = server.getLevel(slot.lastDimension());
        return level != null ? level.getEntity(slot.entity()) : null;
    }

    private static boolean isPending(ResourceLocation village, int slot) {
        for (List<Arrival> list : ARRIVALS.values()) {
            for (Arrival arrival : list) {
                if (arrival.village().equals(village) && arrival.slot() == slot) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- Arrivals ----

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (!ARRIVALS.isEmpty()) {
            Iterator<Map.Entry<UUID, List<Arrival>>> players = ARRIVALS.entrySet().iterator();
            while (players.hasNext()) {
                Map.Entry<UUID, List<Arrival>> entry = players.next();
                ServerPlayer lord = server.getPlayerList().getPlayer(entry.getKey());
                if (lord == null) {
                    players.remove(); // the slots stay empty, so new guards take their posts at home
                    continue;
                }
                List<Arrival> due = new ArrayList<>();
                entry.getValue().removeIf(a -> {
                    if (a.due() <= now) {
                        due.add(a);
                        return true;
                    }
                    return false;
                });
                for (Arrival arrival : due) {
                    if (!arrive(lord, arrival) && arrival.attempts() < 5) {
                        entry.getValue().add(new Arrival(arrival.village(), arrival.slot(), arrival.generation(), now + 40, arrival.attempts() + 1));
                    }
                }
                if (entry.getValue().isEmpty()) {
                    players.remove();
                }
            }
        }
        if (server.getTickCount() % 20 == 7) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncRetinue(player, false);
            }
        }
    }

    /** A guard sent for arrives near the lord, somewhere out of sight with a way through to them. @return whether placed */
    private static boolean arrive(ServerPlayer lord, Arrival arrival) {
        MinecraftServer server = lord.server;
        Optional<VillageRecord> record = FealtyWorldData.get(server).village(arrival.village());
        Optional<Garrison.Slot> slot = record.flatMap(r -> r.garrison().slot(arrival.slot()));
        if (slot.isEmpty() || slot.get().generation() != arrival.generation() || slot.get().entity() != null || slot.get().isDead()
                || !record.get().lord().isLord(lord.getUUID()) || !lord.isAlive() || lord.isSpectator()) {
            return true; // nothing to do any more
        }
        ServerLevel level = lord.serverLevel();
        BlockPos spot = arrivalSpot(level, lord);
        if (spot == null) {
            return false;
        }
        ServerLevel home = server.getLevel(record.get().dimension());
        BlockPos post = record.get().sites().guardPost() != null ? record.get().sites().guardPost() : record.get().center();
        VillageGuardEntity guard = spawn(level, record.get(), arrival.slot(), slot.get(), home != null ? post : spot, spot, 0);
        if (guard == null) {
            return false;
        }
        guard.getData(ModAttachments.GUARD_ORDERS).set(GuardOrders.Mode.FOLLOW, lord.getUUID(), 0, null);
        level.sendParticles(ParticleTypes.CLOUD, guard.getX(), guard.getY() + 0.5, guard.getZ(), 6, 0.3, 0.4, 0.3, 0.01);
        level.playSound(null, guard.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        FealtyWorldData.get(server).setDirty();
        return true;
    }

    /** 18 to 28 blocks from the lord, on safe ground, out of their sight if possible, and with a path to them. */
    @Nullable
    private static BlockPos arrivalSpot(ServerLevel level, ServerPlayer lord) {
        RandomSource random = level.getRandom();
        Vec3 look = lord.getLookAngle();
        BlockPos fallback = null;
        VillageGuardEntity probe = ModEntities.VILLAGE_GUARD.get().create(level);
        for (int tries = 0; tries < 32; tries++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 18 + random.nextDouble() * 10;
            int x = Mth.floor(lord.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(lord.getZ() + Math.sin(angle) * distance);
            Optional<BlockPos> spot = SpawnSpots.nearY(level, x, z, lord.getBlockY(), 8);
            if (spot.isEmpty()) {
                continue;
            }
            BlockPos pos = spot.get();
            if (probe != null) {
                probe.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                probe.setOnGround(true); // not in the world yet, so not on the ground: paths are only made from the ground
                Path path = probe.getNavigation().createPath(lord, 1);
                if (path == null || !path.canReach()) {
                    continue;
                }
            }
            Vec3 eyes = lord.getEyePosition();
            Vec3 target = Vec3.atCenterOf(pos.above());
            Vec3 toward = target.subtract(eyes).normalize();
            boolean behind = look.dot(toward) < 0.25;
            boolean hidden = level.clip(new ClipContext(eyes, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, lord)).getType()
                    != HitResult.Type.MISS;
            if (behind || hidden) {
                return pos;
            }
            if (fallback == null) {
                fallback = pos;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        for (int tries = 0; tries < 16; tries++) {
            Optional<BlockPos> spot = SpawnSpots.nearY(level, lord.getBlockX() + random.nextInt(9) - 4, lord.getBlockZ() + random.nextInt(9) - 4,
                    lord.getBlockY(), 3);
            if (spot.isPresent()) {
                return spot.get();
            }
        }
        return null;
    }

    // ---- The retinue bar ----

    /** Tell the player which guards follow them (and who is on the way), when it changed or when asked to. */
    public static void syncRetinue(ServerPlayer player, boolean force) {
        long now = player.level().getGameTime();
        List<RetinuePayload.Member> members = new ArrayList<>();
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FALL_IN_RANGE),
                m -> m.isAlive() && m.hasData(ModAttachments.GUARD_ORDERS))) {
            GuardOrders orders = mob.getData(ModAttachments.GUARD_ORDERS);
            if (!orders.isActive(now) || orders.mode() == GuardOrders.Mode.DEFEND || !player.getUUID().equals(orders.leader())) {
                continue;
            }
            String kind = mob instanceof VillageGuardEntity guard ? guard.rank().getSerializedName() : mob instanceof IronGolem ? "golem" : "other";
            int color = mob instanceof VillageGuardEntity guard ? guard.color() : 0x9E9E9E;
            members.add(new RetinuePayload.Member(mob.getDisplayName(), mob.getHealth(), mob.getMaxHealth(), orders.mode().getSerializedName(),
                    0, kind, color));
        }
        for (Arrival arrival : ARRIVALS.getOrDefault(player.getUUID(), List.of())) {
            Optional<VillageRecord> record = FealtyWorldData.get(player.server).village(arrival.village());
            Optional<Garrison.Slot> slot = record.flatMap(r -> r.garrison().slot(arrival.slot()));
            if (slot.isEmpty()) {
                continue;
            }
            Component name = Component.translatable("entity.fealty.village_guard." + slot.get().rank().getSerializedName() + ".named",
                    slot.get().name().isEmpty() ? "?" : slot.get().name());
            members.add(new RetinuePayload.Member(name, 1.0F, 1.0F, "arriving", (int) Math.max(0, arrival.due() - now),
                    slot.get().rank().getSerializedName(), record.get().color()));
        }
        if (members.size() > 16) {
            members = new ArrayList<>(members.subList(0, 16));
        }
        int hash = members.isEmpty() ? 0 : members.hashCode();
        Integer last = LAST_RETINUE.get(player.getUUID());
        if (force || last == null || last != hash) {
            if (members.isEmpty() && (last == null || last == 0) && !force) {
                return;
            }
            LAST_RETINUE.put(player.getUUID(), hash);
            FealtyNetwork.send(player, new RetinuePayload(members));
        }
    }

    // ---- The horn screen ----

    /** Sneak-using the horn opens its screen: the villages the player rules and what to tell their guards. */
    public static void openHorn(ServerPlayer player, ItemStack horn) {
        List<OpenHornPayload.Village> villages = new ArrayList<>();
        long now = player.level().getGameTime();
        for (VillageRecord record : FealtyWorldData.get(player.server).villages()) {
            if (!record.lord().isLord(player.getUUID())) {
                continue;
            }
            int following = (int) player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(FALL_IN_RANGE), m -> {
                if (!isVillageGuard(m, record) || !m.hasData(ModAttachments.GUARD_ORDERS)) {
                    return false;
                }
                GuardOrders orders = m.getData(ModAttachments.GUARD_ORDERS);
                return orders.isActive(now) && player.getUUID().equals(orders.leader());
            }).size();
            int distance = record.dimension().equals(player.level().dimension())
                    ? (int) Math.sqrt(record.center().distSqr(player.blockPosition())) : -1;
            villages.add(new OpenHornPayload.Village(record.id().toString(), record.name(), record.color(), record.garrison().allAlive(),
                    record.garrison().allTotal(), following, distance));
        }
        String selected = hornVillage(player, horn).map(r -> r.id().toString()).orElse("");
        FealtyNetwork.send(player, new OpenHornPayload(villages, selected, LordsHornItem.order(horn).ordinal(),
                FealtyConfig.HORN_SUMMON_COUNT.get()));
    }

    /** The lord picked a village and an order on the horn screen (and maybe sounded it). */
    public static void hornCommand(ServerPlayer player, String villageId, String orderId, boolean blow) {
        ItemStack horn = LordsHornItem.held(player);
        if (horn.isEmpty()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(villageId);
        Optional<VillageRecord> village = id == null ? Optional.empty() : FealtyWorldData.get(player.server).village(id);
        if (village.isPresent() && village.get().lord().isLord(player.getUUID())) {
            horn.set(ModDataComponents.HORN_VILLAGE.get(), village.get().id());
        }
        HornOrder order = HornOrder.byId(orderId);
        horn.set(ModDataComponents.HORN_ORDER.get(), order.ordinal());
        if (blow && !player.getCooldowns().isOnCooldown(horn.getItem())) {
            blow(player, horn, order);
            player.getCooldowns().addCooldown(horn.getItem(), 60);
        }
    }

    // ---- Friendly fire ----

    /** Guards' arrows pass by villagers, other guards and the players they are not fighting. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (!(projectile.getOwner() instanceof VillageGuardEntity guard) || !(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        Entity victim = hit.getEntity();
        boolean friend = victim instanceof AbstractVillager || victim instanceof VillageGuardEntity || victim instanceof IronGolem
                || (victim instanceof Mob mob && GuardManager.isGuard(mob))
                || (victim instanceof Player && victim != guard.getTarget());
        if (friend) {
            event.setCanceled(true);
        }
    }

    // ---- Cleanup ----

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_RETINUE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ARRIVALS.clear();
        LAST_RETINUE.clear();
    }
}
