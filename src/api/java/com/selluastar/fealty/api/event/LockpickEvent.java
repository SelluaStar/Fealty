package com.selluastar.fealty.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Picking a locked village chest or coffer. Fired on {@code NeoForge.EVENT_BUS}. */
public abstract class LockpickEvent extends Event {
    private final ServerPlayer player;
    private final BlockPos pos;

    protected LockpickEvent(ServerPlayer player, BlockPos pos) {
        this.player = player;
        this.pos = pos;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public BlockPos getPos() {
        return pos;
    }

    /** A try at the lock, before it is judged. Cancel to ignore it (the pick is not strained). */
    public static class Attempt extends LockpickEvent implements ICancellableEvent {
        private final float angle;

        public Attempt(ServerPlayer player, BlockPos pos, float angle) {
            super(player, pos);
            this.angle = angle;
        }

        /** Where the pick was set, 0 to 180 degrees. */
        public float getAngle() {
            return angle;
        }
    }

    /** The lock opened. */
    public static class Opened extends LockpickEvent {
        private final boolean coffer;

        public Opened(ServerPlayer player, BlockPos pos, boolean coffer) {
            super(player, pos);
            this.coffer = coffer;
        }

        public boolean isCoffer() {
            return coffer;
        }
    }

    /** The pick snapped. */
    public static class Broke extends LockpickEvent {
        public Broke(ServerPlayer player, BlockPos pos) {
            super(player, pos);
        }
    }
}
