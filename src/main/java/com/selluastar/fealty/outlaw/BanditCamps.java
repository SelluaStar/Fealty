package com.selluastar.fealty.outlaw;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.config.FealtyConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Every bandit camp the world has generated ({@code data/fealty_camps.dat}). Only one camp in an area is active: the
 * first one found holds the ground, and any other within {@code camp_exclusion_radius} of an active camp stays an
 * empty ruin. Only active camps fill with bandits and send raiding parties.
 */
public final class BanditCamps extends SavedData {
    private static final String NAME = "fealty_camps";
    private static final SavedData.Factory<BanditCamps> FACTORY = new SavedData.Factory<>(BanditCamps::new, BanditCamps::load, null);

    private final List<GlobalPos> camps = new ArrayList<>();

    public static BanditCamps get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    private static BanditCamps load(CompoundTag tag, HolderLookup.Provider registries) {
        BanditCamps data = new BanditCamps();
        if (tag.contains("camps")) {
            GlobalPos.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("camps"))
                    .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to load bandit camps: {}", e))
                    .ifPresent(data.camps::addAll);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        GlobalPos.CODEC.listOf().encodeStart(NbtOps.INSTANCE, camps)
                .resultOrPartial(e -> Fealty.LOGGER.error("Fealty: failed to save bandit camps: {}", e))
                .ifPresent(t -> tag.put("camps", t));
        return tag;
    }

    public void register(ServerLevel level, BlockPos pos) {
        GlobalPos camp = GlobalPos.of(level.dimension(), pos.immutable());
        if (!camps.contains(camp)) {
            camps.add(camp);
            setDirty();
        }
    }

    /** The standard is gone: the camp is no more, and a camp it kept quiet may take its place. */
    public void unregister(ServerLevel level, BlockPos pos) {
        if (camps.remove(GlobalPos.of(level.dimension(), pos))) {
            setDirty();
        }
    }

    /** Active camps: in the order they were found, each one not within the exclusion radius of an earlier active camp. */
    public List<GlobalPos> active() {
        double radius = FealtyConfig.CAMP_EXCLUSION_RADIUS.get();
        double r2 = radius * radius;
        List<GlobalPos> result = new ArrayList<>();
        for (GlobalPos camp : camps) {
            boolean crowded = false;
            for (GlobalPos other : result) {
                if (other.dimension().equals(camp.dimension()) && other.pos().distSqr(camp.pos()) < r2) {
                    crowded = true;
                    break;
                }
            }
            if (!crowded) {
                result.add(camp);
            }
        }
        return result;
    }

    public boolean isActive(ServerLevel level, BlockPos pos) {
        return active().contains(GlobalPos.of(level.dimension(), pos));
    }

    /** The nearest active camp within {@code radius} blocks. */
    public Optional<BlockPos> nearestActive(ServerLevel level, BlockPos pos, double radius) {
        BlockPos best = null;
        double bestDistance = radius * radius;
        for (GlobalPos camp : active()) {
            if (!camp.dimension().equals(level.dimension())) {
                continue;
            }
            double distance = camp.pos().distSqr(pos);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = camp.pos();
            }
        }
        return Optional.ofNullable(best);
    }
}
