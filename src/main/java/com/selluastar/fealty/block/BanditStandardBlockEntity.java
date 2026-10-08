package com.selluastar.fealty.block;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.entity.BanditEntity;
import com.selluastar.fealty.entity.BlackMarketeerEntity;
import com.selluastar.fealty.entity.GuildFenceEntity;
import com.selluastar.fealty.outlaw.BanditCamps;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.registry.ModBlockEntities;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.util.SpawnSpots;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The heart of a bandit camp. A camp's standard (only one the world generated, never one a player placed) mans the
 * camp with bandits, a captain, a black marketeer and a fence, and refills it days after it is cleared. Bandits
 * turn up on open ground a little way off and walk in. Nothing spawns until every chunk around the camp has its
 * entities loaded, so a camp is never manned twice over.
 */
public class BanditStandardBlockEntity extends BlockEntity {
    private static final long REFILL_DELAY = 72000;
    private static final int BANDITS = 4;
    /** Camp members stay within this many blocks of the standard. */
    private static final int MEMBER_RANGE = 40;
    private boolean natural;
    private boolean populated;
    private long clearedAt = -1;
    private long lastCheck;
    private boolean registered;

    public BanditStandardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BANDIT_STANDARD.get(), pos, state);
    }

    /** Set when the camp is generated: only natural standards man a camp. */
    public void setNatural() {
        natural = true;
        setChanged();
    }

    public boolean isNatural() {
        return natural;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BanditStandardBlockEntity standard) {
        if (!(level instanceof ServerLevel server) || !standard.natural) {
            return;
        }
        long now = level.getGameTime();
        if (now - standard.lastCheck < (standard.populated ? 1200 : 40)) {
            return;
        }
        standard.lastCheck = now;
        BanditCamps camps = BanditCamps.get(server.getServer());
        if (!standard.registered) {
            camps.register(server, pos);
            standard.registered = true;
        }
        if (!camps.isActive(server, pos) || !entitiesLoaded(server, pos)) {
            return;
        }
        if (!standard.populated) {
            standard.populated = true;
            standard.populate(server, pos, true);
            standard.setChanged();
            return;
        }
        if (standard.clearedAt >= 0 && now - standard.clearedAt < REFILL_DELAY) {
            return;
        }
        if (server.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 48, false) != null) {
            return;
        }
        standard.populate(server, pos, false);
        standard.clearedAt = -1;
        standard.setChanged();
    }

    /** Whether every chunk camp members could be in has its entities loaded. */
    private static boolean entitiesLoaded(ServerLevel level, BlockPos pos) {
        int range = MEMBER_RANGE + 8;
        for (int cx = (pos.getX() - range) >> 4; cx <= (pos.getX() + range) >> 4; cx++) {
            for (int cz = (pos.getZ() - range) >> 4; cz <= (pos.getZ() + range) >> 4; cz++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The captain fell: the camp stays quiet for a few days. */
    public void markCleared(long gameTime) {
        clearedAt = gameTime;
        setChanged();
    }

    private void populate(ServerLevel level, BlockPos pos, boolean initial) {
        long camp = pos.asLong();
        List<Mob> members = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(MEMBER_RANGE),
                m -> m.isAlive() && m.hasData(ModAttachments.CAMP) && m.getData(ModAttachments.CAMP) == camp);
        long bandits = members.stream().filter(m -> m.getType() == ModEntities.BANDIT.get()).count();
        boolean captain = members.stream().anyMatch(m -> m.getType() == ModEntities.BANDIT_CAPTAIN.get());
        boolean marketeer = members.stream().anyMatch(m -> m instanceof BlackMarketeerEntity);
        boolean fence = members.stream().anyMatch(m -> m instanceof GuildFenceEntity);
        RandomSource random = level.getRandom();
        int wanted = BANDITS + (initial ? random.nextInt(3) : 0);
        for (long i = bandits; i < wanted; i++) {
            spawn(level, pos, ModEntities.BANDIT.get(), random, true);
        }
        if (!captain) {
            spawn(level, pos, ModEntities.BANDIT_CAPTAIN.get(), random, true);
        }
        if (!marketeer) {
            spawn(level, pos, ModEntities.BLACK_MARKETEER.get(), random, false);
        }
        if (!fence && (!initial || random.nextFloat() < 0.6F)) {
            spawn(level, pos, ModEntities.GUILD_FENCE.get(), random, false);
        }
    }

    /**
     * @param walkIn bandits turn up 16 to 30 blocks off, out of sight if they can, and walk to the camp; traders
     *               appear by the fire
     */
    private static void spawn(ServerLevel level, BlockPos standard, EntityType<? extends Mob> type, RandomSource random, boolean walkIn) {
        Mob mob = type.create(level);
        if (mob == null) {
            return;
        }
        Optional<BlockPos> spot = walkIn ? findSpot(level, standard, mob, random, 16, 30, true) : Optional.empty();
        if (spot.isEmpty()) {
            spot = findSpot(level, standard, mob, random, 3, 9, false);
        }
        if (spot.isEmpty()) {
            mob.discard();
            return;
        }
        BlockPos pos = spot.get();
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360F, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        mob.setPersistenceRequired();
        if (mob instanceof BanditEntity bandit) {
            bandit.bindToCamp(standard);
        } else {
            mob.setData(ModAttachments.CAMP, standard.asLong());
            if (mob instanceof PathfinderMob pathfinder) {
                pathfinder.restrictTo(standard, 12);
            }
        }
        level.addFreshEntity(mob);
    }

    /**
     * Dry, solid ground with room to stand, between {@code min} and {@code max} blocks from the standard, from where
     * the mob can walk to it. With {@code hidden}, a spot no nearby player can see is preferred.
     */
    public static Optional<BlockPos> findSpot(ServerLevel level, BlockPos target, Mob probe, RandomSource random, int min, int max, boolean hidden) {
        Player watcher = hidden ? level.getNearestPlayer(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 96, false) : null;
        BlockPos seen = null;
        for (int tries = 0; tries < 24; tries++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = min + random.nextDouble() * (max - min);
            int x = Mth.floor(target.getX() + 0.5 + Math.cos(angle) * distance);
            int z = Mth.floor(target.getZ() + 0.5 + Math.sin(angle) * distance);
            Optional<BlockPos> spot = SpawnSpots.nearY(level, x, z, target.getY(), 10);
            if (spot.isEmpty() || !canWalk(probe, spot.get(), target)) {
                continue;
            }
            if (watcher == null || !canSee(level, watcher, spot.get())) {
                return spot;
            }
            if (seen == null) {
                seen = spot.get();
            }
        }
        return Optional.ofNullable(seen);
    }

    /** Whether the mob, standing at {@code from}, has a path to {@code to}. */
    public static boolean canWalk(Mob probe, BlockPos from, BlockPos to) {
        probe.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        probe.setOnGround(true);
        Path path = probe.getNavigation().createPath(to, 2);
        return path != null && path.canReach();
    }

    private static boolean canSee(ServerLevel level, Player player, BlockPos spot) {
        Vec3 eyes = player.getEyePosition();
        Vec3 target = Vec3.atCenterOf(spot.above());
        return level.clip(new ClipContext(eyes, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        populated = tag.getBoolean("Populated");
        // Camps from before natural standards were marked were all generated ones.
        natural = tag.contains("Natural") ? tag.getBoolean("Natural") : populated;
        clearedAt = tag.contains("ClearedAt") ? tag.getLong("ClearedAt") : -1;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Natural", natural);
        tag.putBoolean("Populated", populated);
        tag.putLong("ClearedAt", clearedAt);
    }

    @Nullable
    public static BanditStandardBlockEntity at(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof BanditStandardBlockEntity standard ? standard : null;
    }
}
