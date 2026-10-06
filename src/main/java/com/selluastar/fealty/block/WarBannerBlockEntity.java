package com.selluastar.fealty.block;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.api.Stronghold;
import com.selluastar.fealty.api.event.StrongholdEvent;
import com.selluastar.fealty.registry.ModAttachments;
import com.selluastar.fealty.registry.ModBlockEntities;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.war.Campaigns;
import com.selluastar.fealty.war.Captives;
import com.selluastar.fealty.war.Strongholds;
import com.selluastar.fealty.war.WarDefenders;
import com.selluastar.fealty.world.PillagerCampPiece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The heart of a pillager camp. A generated War Banner registers its camp as a stronghold, mans it with pillagers,
 * vindicators and a banner-bearing captain, and puts captives in its cages. While the camp is razed the banner hangs
 * in tatters with the razing village's colours flying beside it; when the raze runs out the pillagers return.
 * Nothing spawns until every chunk around the camp has its entities loaded, and never mid-battle.
 */
public class WarBannerBlockEntity extends BlockEntity {
    /** Garrison members stay within this many blocks of the banner. */
    public static final int MEMBER_RANGE = 32;
    private static final int PILLAGERS = 3;
    private static final int VINDICATORS = 1;
    private boolean natural;
    private boolean populated;
    private boolean captivesPlaced;
    @Nullable
    private BlockPos victoryBanner;
    private long lastCheck;

    public WarBannerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WAR_BANNER.get(), pos, state);
    }

    /** Set when the camp is generated: only generated banners keep a camp. */
    public void setNatural() {
        natural = true;
        setChanged();
    }

    public boolean isNatural() {
        return natural;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WarBannerBlockEntity banner) {
        if (!(level instanceof ServerLevel server) || !banner.natural) {
            return;
        }
        long now = level.getGameTime();
        if (now - banner.lastCheck < (banner.populated ? 600 : 40)) {
            return;
        }
        banner.lastCheck = now;
        Strongholds strongholds = Strongholds.get(server.getServer());
        Strongholds.Entry entry = strongholds.register(server, pos, Stronghold.Kind.CAMP, Strongholds.PILLAGER_CAMP, 20,
                StrongholdEvent.Discovered.How.GENERATED);
        long day = RepManager.day(server.getServer());
        boolean razed = entry.isRazed(day);
        if (state.getValue(WarBannerBlock.RAZED) != razed) {
            server.setBlock(pos, state.setValue(WarBannerBlock.RAZED, razed), Block.UPDATE_ALL);
            if (!razed) {
                banner.lowerVictoryBanner(server);
                banner.captivesPlaced = false;
            }
            banner.setChanged();
        }
        if (razed || !entitiesLoaded(server, pos) || Campaigns.fightingAt(server, entry)) {
            return;
        }
        if (!banner.populated) {
            banner.populated = true;
            banner.man(server, pos, true);
        } else if (server.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 64, false) == null) {
            banner.man(server, pos, false);
        }
        if (!banner.captivesPlaced) {
            banner.captivesPlaced = true;
            int placed = Captives.fillCages(server, pos, PillagerCampPiece.CAGES);
            entry.setCaptives(placed);
            strongholds.setDirty();
        }
        banner.setChanged();
    }

    /** Whether every chunk garrison members could be in has its entities loaded. */
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

    /** Fill the garrison up to strength: pillagers, vindicators and one captain. */
    private void man(ServerLevel level, BlockPos pos, boolean initial) {
        List<Mob> members = garrison(level, pos);
        RandomSource random = level.getRandom();
        long pillagers = members.stream().filter(m -> m.getType() == EntityType.PILLAGER && !isCaptain(m)).count();
        long vindicators = members.stream().filter(m -> m.getType() == EntityType.VINDICATOR).count();
        boolean captain = members.stream().anyMatch(WarBannerBlockEntity::isCaptain);
        int wantPillagers = PILLAGERS + (initial ? random.nextInt(3) : 1);
        int wantVindicators = VINDICATORS + (initial ? random.nextInt(2) : 0);
        for (long i = pillagers; i < wantPillagers; i++) {
            spawn(level, pos, EntityType.PILLAGER, random, false);
        }
        for (long i = vindicators; i < wantVindicators; i++) {
            spawn(level, pos, EntityType.VINDICATOR, random, false);
        }
        if (!captain) {
            spawn(level, pos, EntityType.PILLAGER, random, true);
        }
    }

    /** The camp's living garrison. */
    public static List<Mob> garrison(ServerLevel level, BlockPos pos) {
        long camp = pos.asLong();
        return level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(MEMBER_RANGE + 16),
                m -> m.isAlive() && m.hasData(ModAttachments.CAMP) && m.getData(ModAttachments.CAMP) == camp);
    }

    private static boolean isCaptain(Mob mob) {
        return mob instanceof PatrollingMonster patrolling && patrolling.isPatrolLeader();
    }

    private static void spawn(ServerLevel level, BlockPos banner, EntityType<? extends Mob> type, RandomSource random, boolean captain) {
        Mob mob = type.create(level);
        if (mob == null) {
            return;
        }
        Optional<BlockPos> spot = BanditStandardBlockEntity.findSpot(level, banner, mob, random, 3, 11, false);
        if (spot.isEmpty()) {
            mob.discard();
            return;
        }
        BlockPos pos = spot.get();
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360F, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        mob.setPersistenceRequired();
        mob.setData(ModAttachments.CAMP, banner.asLong());
        if (mob instanceof PatrollingMonster patrolling) {
            patrolling.setPatrolling(false);
            patrolling.setPatrolLeader(captain);
            if (captain) {
                HolderLookup.RegistryLookup<net.minecraft.world.level.block.entity.BannerPattern> patterns =
                        level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
                patrolling.setItemSlot(EquipmentSlot.HEAD, Raid.getLeaderBannerInstance(patterns));
                patrolling.setDropChance(EquipmentSlot.HEAD, 2.0F);
            }
        }
        if (mob instanceof PathfinderMob pathfinder) {
            pathfinder.restrictTo(banner, 18);
        }
        WarDefenders.enlist(mob);
        level.addFreshEntity(mob);
    }

    /** Fly the razing village's colours beside the tattered banner. */
    public void raiseVictoryBanner(ServerLevel level, BlockState bannerState) {
        BlockPos spot = worldPosition.east();
        if (!level.getBlockState(spot).canBeReplaced()) {
            spot = worldPosition.west();
        }
        if (level.getBlockState(spot).canBeReplaced()) {
            level.setBlock(spot, bannerState, Block.UPDATE_ALL);
            victoryBanner = spot.immutable();
            setChanged();
        }
    }

    private void lowerVictoryBanner(ServerLevel level) {
        if (victoryBanner != null && level.getBlockState(victoryBanner).is(net.minecraft.tags.BlockTags.BANNERS)) {
            level.setBlock(victoryBanner, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        victoryBanner = null;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        natural = tag.getBoolean("Natural");
        populated = tag.getBoolean("Populated");
        captivesPlaced = tag.getBoolean("CaptivesPlaced");
        victoryBanner = NbtUtils.readBlockPos(tag, "VictoryBanner").orElse(null);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Natural", natural);
        tag.putBoolean("Populated", populated);
        tag.putBoolean("CaptivesPlaced", captivesPlaced);
        if (victoryBanner != null) {
            tag.put("VictoryBanner", NbtUtils.writeBlockPos(victoryBanner));
        }
    }

    @Nullable
    public static WarBannerBlockEntity at(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof WarBannerBlockEntity banner ? banner : null;
    }
}
