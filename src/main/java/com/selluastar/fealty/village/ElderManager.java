package com.selluastar.fealty.village;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.block.VillageCofferBlock;
import com.selluastar.fealty.block.VillageCofferBlockEntity;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.entity.VillageElderEntity;
import com.selluastar.fealty.registry.ModBlocks;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.rep.FealtyWorldData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Spawns, watches and replaces village elders. An elder is placed indoors near the bell the first time a player
 * visits a castle village, with the village coffer beside them.
 */
public final class ElderManager {
    public static final ResourceKey<LootTable> COFFER_LOOT = ResourceKey.create(Registries.LOOT_TABLE, Fealty.id("chests/village_coffer"));
    private static final Map<String, Integer> MISSING = new HashMap<>();

    private ElderManager() {
    }

    /** Called every few seconds while a player is in the village. */
    public static void tickVillage(ServerLevel level, VillageRecord record) {
        if (!record.hasElder() || !record.dimension().equals(level.dimension())) {
            return;
        }
        VillageRecord.ElderInfo elder = record.elder();
        switch (elder.state()) {
            case NONE -> {
                if (level.isLoaded(record.center()) && level.areEntitiesLoaded(ChunkPos.asLong(record.center()))) {
                    spawnElder(level, record, null);
                }
            }
            case ALIVE -> watchElder(level, record);
            case BROKEN -> {
            }
        }
    }

    private static void watchElder(ServerLevel level, VillageRecord record) {
        VillageRecord.ElderInfo elder = record.elder();
        BlockPos home = elder.home() != null ? elder.home() : record.center();
        if (!level.isLoaded(home) || !level.areEntitiesLoaded(ChunkPos.asLong(home))) {
            return;
        }
        Entity entity = elder.uuid() != null ? level.getEntity(elder.uuid()) : null;
        String key = record.id().toString();
        if (entity != null && entity.isAlive()) {
            MISSING.remove(key);
            return;
        }
        // Not found where it should be. Give it a while (it may be in an unloaded chunk nearby), then replace it.
        int misses = MISSING.merge(key, 1, Integer::sum);
        if (misses >= 12) {
            MISSING.remove(key);
            Fealty.LOGGER.info("Fealty: the elder of {} went missing; a new one takes their place", record.name());
            spawnElder(level, record, null);
        }
    }

    private static boolean spawnElder(ServerLevel level, VillageRecord record, @Nullable Villager successor) {
        VillageRecord.ElderInfo elder = record.elder();
        BlockPos home = elder.home() != null ? elder.home() : findHome(level, record);
        if (home == null) {
            return false;
        }
        VillageElderEntity entity = ModEntities.VILLAGE_ELDER.get().create(level);
        if (entity == null) {
            return false;
        }
        String name = VillageNames.elderName(level.getRandom());
        if (successor != null) {
            entity.moveTo(successor.getX(), successor.getY(), successor.getZ(), successor.getYRot(), 0);
        } else {
            entity.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, level.getRandom().nextFloat() * 360F, 0);
        }
        entity.bindTo(record, home, name);
        level.addFreshEntity(entity);
        if (successor != null) {
            successor.discard();
        }
        elder.setUuid(entity.getUUID());
        elder.setState(VillageRecord.ElderState.ALIVE);
        elder.setHome(home);
        elder.setName(name);
        if (FealtyConfig.PLACE_COFFERS.get() && elder.coffer() == null) {
            placeCoffer(level, record, home);
        }
        record.setInitialized(true);
        FealtyWorldData.get(level.getServer()).setDirty();
        return true;
    }

    /** An indoor spot near the bell, preferring roofed floor space close to the centre. */
    @Nullable
    static BlockPos findHome(ServerLevel level, VillageRecord record) {
        BlockPos center = record.center();
        RandomSource random = level.getRandom();
        BlockPos best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < 600; i++) {
            BlockPos pos = center.offset(random.nextInt(41) - 20, random.nextInt(13) - 6, random.nextInt(41) - 20);
            if (!level.isLoaded(pos) || !record.contains(level.dimension(), pos) || !isStandable(level, pos)) {
                continue;
            }
            int score = -(int) Math.sqrt(pos.distSqr(center));
            if (isIndoors(level, pos)) {
                score += 25;
            }
            if (score > bestScore) {
                bestScore = score;
                best = pos.immutable();
            }
        }
        if (best == null && level.isLoaded(center)) {
            best = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center);
        }
        return best;
    }

    static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockPos below = pos.below();
        return feet.getCollisionShape(level, pos).isEmpty() && feet.getFluidState().isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty() && head.getFluidState().isEmpty()
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static boolean isIndoors(ServerLevel level, BlockPos pos) {
        if (level.canSeeSky(pos)) {
            return false;
        }
        for (int dy = 2; dy <= 6; dy++) {
            if (!level.getBlockState(pos.above(dy)).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static void placeCoffer(ServerLevel level, VillageRecord record, BlockPos home) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos pos = home.relative(dir);
            if (!isStandable(level, pos)) {
                continue;
            }
            BlockState state = ModBlocks.VILLAGE_COFFER.get().defaultBlockState().setValue(VillageCofferBlock.FACING, dir.getOpposite());
            level.setBlock(pos, state, Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof VillageCofferBlockEntity coffer) {
                coffer.setVillage(record.id());
                coffer.setLootTable(COFFER_LOOT, level.getRandom().nextLong());
            }
            record.elder().setCoffer(pos);
            return;
        }
    }

    public static void onElderDeath(VillageElderEntity elder, DamageSource source) {
        if (elder.village() == null || elder.getServer() == null) {
            return;
        }
        FealtyWorldData data = FealtyWorldData.get(elder.getServer());
        Optional<VillageRecord> record = data.village(elder.village());
        if (record.isEmpty() || record.get().elder().uuid() == null || !record.get().elder().uuid().equals(elder.getUUID())) {
            return;
        }
        VillageRecord village = record.get();
        village.elder().setState(VillageRecord.ElderState.BROKEN);
        village.elder().setUuid(null);
        village.elder().setBrokenSince(elder.level().getGameTime());
        data.setDirty();
        Component message = Component.translatable("fealty.village.broken", village.elder().name(), village.name()).withStyle(ChatFormatting.DARK_RED);
        for (ServerPlayer player : elder.getServer().getPlayerList().getPlayers()) {
            if (village.contains(player.level().dimension(), player.blockPosition())) {
                player.sendSystemMessage(message);
            }
        }
        if (source.getEntity() instanceof ServerPlayer killer) {
            FealtyEvents.fire(killer, FealtyEvents.VILLAGE_BROKEN);
        }
    }

    /**
     * Bring a Broken village back. With a successor, that villager becomes the new elder at once; otherwise a new
     * elder appears the next time the village is loaded.
     *
     * @return whether the elder was placed right away
     */
    public static boolean restore(MinecraftServer server, VillageRecord record, Optional<Villager> successor) {
        record.elder().setState(VillageRecord.ElderState.NONE);
        record.elder().setUuid(null);
        FealtyWorldData.get(server).setDirty();
        ServerLevel level = server.getLevel(record.dimension());
        if (level == null) {
            return false;
        }
        if (successor.isPresent() && successor.get().level() == level) {
            return spawnElder(level, record, successor.get());
        }
        BlockPos home = record.elder().home() != null ? record.elder().home() : record.center();
        if (level.isLoaded(home)) {
            return spawnElder(level, record, null);
        }
        return false;
    }
}
