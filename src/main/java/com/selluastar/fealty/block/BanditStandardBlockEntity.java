package com.selluastar.fealty.block;

import java.util.List;

import com.selluastar.fealty.entity.BanditEntity;
import com.selluastar.fealty.entity.BlackMarketeerEntity;
import com.selluastar.fealty.entity.GuildFenceEntity;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.registry.ModBlockEntities;
import com.selluastar.fealty.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Spawns a camp's bandits, captain, black marketeer and fence, and refills the camp days after it is cleared. */
public class BanditStandardBlockEntity extends BlockEntity {
    private static final long REFILL_DELAY = 72000;
    private static final int BANDITS = 4;
    private boolean populated;
    private long clearedAt = -1;
    private long lastCheck;

    public BanditStandardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BANDIT_STANDARD.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BanditStandardBlockEntity standard) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!standard.populated) {
            standard.populated = true;
            standard.populate(server, pos, true);
            standard.setChanged();
            return;
        }
        long now = level.getGameTime();
        if (now - standard.lastCheck < 1200) {
            return;
        }
        standard.lastCheck = now;
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

    /** The captain fell: the camp stays quiet for a few days. */
    public void markCleared(long gameTime) {
        clearedAt = gameTime;
        setChanged();
    }

    private void populate(ServerLevel level, BlockPos pos, boolean initial) {
        long camp = pos.asLong();
        List<Mob> members = level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(40),
                m -> m.isAlive() && m.hasData(ModAttachments.CAMP) && m.getData(ModAttachments.CAMP) == camp);
        long bandits = members.stream().filter(m -> m.getType() == ModEntities.BANDIT.get()).count();
        boolean captain = members.stream().anyMatch(m -> m.getType() == ModEntities.BANDIT_CAPTAIN.get());
        boolean marketeer = members.stream().anyMatch(m -> m instanceof BlackMarketeerEntity);
        boolean fence = members.stream().anyMatch(m -> m instanceof GuildFenceEntity);
        RandomSource random = level.getRandom();
        int wanted = BANDITS + (initial ? random.nextInt(3) : 0);
        for (long i = bandits; i < wanted; i++) {
            spawn(level, pos, ModEntities.BANDIT.get(), random);
        }
        if (!captain) {
            spawn(level, pos, ModEntities.BANDIT_CAPTAIN.get(), random);
        }
        if (!marketeer) {
            spawn(level, pos, ModEntities.BLACK_MARKETEER.get(), random);
        }
        if (!fence && (!initial || random.nextFloat() < 0.6F)) {
            spawn(level, pos, ModEntities.GUILD_FENCE.get(), random);
        }
    }

    private static void spawn(ServerLevel level, BlockPos standard, EntityType<? extends Mob> type, RandomSource random) {
        Mob mob = type.create(level);
        if (mob == null) {
            return;
        }
        BlockPos column = standard.offset(random.nextInt(13) - 6, 0, random.nextInt(13) - 6);
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
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

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        populated = tag.getBoolean("Populated");
        clearedAt = tag.contains("ClearedAt") ? tag.getLong("ClearedAt") : -1;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Populated", populated);
        tag.putLong("ClearedAt", clearedAt);
    }
}
