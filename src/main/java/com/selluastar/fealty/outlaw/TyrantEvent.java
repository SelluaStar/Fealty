package com.selluastar.fealty.outlaw;

import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.selluastar.fealty.advancement.FealtyEvents;
import com.selluastar.fealty.api.RepSources;
import com.selluastar.fealty.config.FealtyConfig;
import com.selluastar.fealty.entity.TyrantLordEntity;
import com.selluastar.fealty.registry.ModEntities;
import com.selluastar.fealty.rep.FealtyWorldData;
import com.selluastar.fealty.rep.RepManager;
import com.selluastar.fealty.village.VillageRecord;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Tyrant Lord event. When a player's wanted level reaches 2, the Tyrant seizes a castle village (the one that
 * hates them, or the nearest with an elder) and waits there. He appears when the player comes to that village.
 */
public final class TyrantEvent {
    private TyrantEvent() {
    }

    /** Whether any village has the Tyrant waiting for this player. */
    public static boolean isHunted(ServerPlayer player) {
        for (VillageRecord record : FealtyWorldData.get(player.server).villages()) {
            if (player.getUUID().equals(record.tyrantFor())) {
                return true;
            }
        }
        return false;
    }

    public static void begin(ServerPlayer player, ResourceLocation faction) {
        if (!FealtyConfig.ENABLE_TYRANT.get() || isHunted(player)) {
            return;
        }
        FealtyWorldData data = FealtyWorldData.get(player.server);
        VillageRecord seat = data.village(faction).filter(VillageRecord::hasElder).orElse(null);
        if (seat == null) {
            double best = Double.MAX_VALUE;
            for (VillageRecord record : data.villages()) {
                if (record.hasElder() && record.dimension().equals(player.level().dimension())) {
                    double d = record.center().distSqr(player.blockPosition());
                    if (d < best) {
                        best = d;
                        seat = record;
                    }
                }
            }
        }
        if (seat == null) {
            return;
        }
        seat.setTyrantFor(player.getUUID());
        data.setDirty();
        player.sendSystemMessage(Component.translatable("fealty.tyrant.risen", seat.name(), seat.center().getX(), seat.center().getZ())
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        FealtyEvents.fire(player, FealtyEvents.TYRANT_RISEN);
    }

    /** The player is no longer wanted enough: the Tyrant goes home. */
    public static void cancelFor(ServerPlayer player, ResourceLocation faction) {
        FealtyWorldData data = FealtyWorldData.get(player.server);
        for (VillageRecord record : data.villages()) {
            if (player.getUUID().equals(record.tyrantFor())) {
                record.setTyrantFor(null);
                data.setDirty();
                player.sendSystemMessage(Component.translatable("fealty.tyrant.withdraws", record.name()));
            }
        }
    }

    /** Spawn the Tyrant when the hunted player arrives at his seat. */
    public static void onPlayerInVillage(ServerPlayer player, VillageRecord village) {
        if (!player.getUUID().equals(village.tyrantFor()) || village.isTyrantSpawned() || player.isCreative() || player.isSpectator()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos base = village.elder().home() != null ? village.elder().home() : village.center();
        if (!level.isLoaded(base)) {
            return;
        }
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);
        TyrantLordEntity tyrant = ModEntities.TYRANT_LORD.get().create(level);
        if (tyrant == null) {
            return;
        }
        tyrant.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        tyrant.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        tyrant.restrictTo(village.center(), 48);
        tyrant.setQuarry(player, village.id());
        level.addFreshEntity(tyrant);
        village.setTyrantSpawned(true);
        FealtyWorldData.get(player.server).setDirty();
        player.sendSystemMessage(Component.translatable("fealty.tyrant.appears", village.name()).withStyle(ChatFormatting.DARK_RED));
    }

    public static void onTyrantSlain(TyrantLordEntity tyrant, DamageSource source) {
        MinecraftServer server = tyrant.getServer();
        if (server == null) {
            return;
        }
        FealtyWorldData data = FealtyWorldData.get(server);
        Optional<VillageRecord> village = tyrant.village() == null ? Optional.empty() : data.village(tyrant.village());
        @Nullable UUID hunted = village.map(VillageRecord::tyrantFor).orElse(null);
        village.ifPresent(v -> v.setTyrantFor(null));
        data.setDirty();
        if (source.getEntity() instanceof ServerPlayer killer) {
            HeatManager.clearAll(killer);
            village.ifPresent(v -> {
                RepManager.meet(killer, v.id());
                RepManager.change(killer, v.id(), RepManager.baseAmount(RepSources.LIBERATION), RepSources.LIBERATION);
            });
            FealtyEvents.fire(killer, FealtyEvents.TYRANT_SLAIN);
            Component message = Component.translatable("fealty.tyrant.slain", killer.getDisplayName(),
                    village.map(VillageRecord::name).orElse("?")).withStyle(ChatFormatting.GOLD);
            server.getPlayerList().broadcastSystemMessage(message, false);
        } else if (hunted != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(hunted);
            if (player != null) {
                // Someone else ended him; the hunt is over, but the heat remains.
                player.sendSystemMessage(Component.translatable("fealty.tyrant.fallen_other"));
            }
        }
    }
}
