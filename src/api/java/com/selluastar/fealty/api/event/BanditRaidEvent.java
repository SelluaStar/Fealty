package com.selluastar.fealty.api.event;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** A bandit raid on a village. Fired on {@code NeoForge.EVENT_BUS}. */
public abstract class BanditRaidEvent extends Event {
    private final ServerLevel level;
    private final ResourceLocation village;

    protected BanditRaidEvent(ServerLevel level, ResourceLocation village) {
        this.level = level;
        this.village = village;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public ResourceLocation getVillage() {
        return village;
    }

    /** A raiding party is about to set out. Cancel it, or change its size. */
    public static class Start extends BanditRaidEvent implements ICancellableEvent {
        @Nullable
        private final BlockPos camp;
        private int size;

        public Start(ServerLevel level, ResourceLocation village, @Nullable BlockPos camp, int size) {
            super(level, village);
            this.camp = camp;
            this.size = size;
        }

        /** The bandit camp they come from, if any. */
        public Optional<BlockPos> getCamp() {
            return Optional.ofNullable(camp);
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = Math.max(1, size);
        }
    }

    /** The last raider fell. */
    public static class Won extends BanditRaidEvent {
        private final List<ServerPlayer> defenders;

        public Won(ServerLevel level, ResourceLocation village, List<ServerPlayer> defenders) {
            super(level, village);
            this.defenders = List.copyOf(defenders);
        }

        public List<ServerPlayer> getDefenders() {
            return defenders;
        }
    }

    /** Time ran out and the bandits made off, with whatever they took. */
    public static class Withdrew extends BanditRaidEvent {
        private final List<ItemStack> taken;

        public Withdrew(ServerLevel level, ResourceLocation village, List<ItemStack> taken) {
            super(level, village);
            this.taken = List.copyOf(taken);
        }

        public List<ItemStack> getTaken() {
            return taken;
        }
    }
}
