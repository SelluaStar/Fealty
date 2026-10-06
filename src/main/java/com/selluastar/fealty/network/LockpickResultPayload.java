package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: how a try at the lock went. {@code turn} is how far the lock turned before it stuck (1 when it
 * opens), so the closer the pick, the further it turns.
 */
public record LockpickResultPayload(float turn, int usesLeft, int spare, int flags) implements CustomPacketPayload {
    public static final int BROKE = 1;
    public static final int OPENED = 2;
    public static final int HEARD = 4;
    /** The attempt is over without a result (the pick is gone, or the player walked off). */
    public static final int CLOSED = 8;

    public static final Type<LockpickResultPayload> TYPE = new Type<>(Fealty.id("lockpick_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LockpickResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, LockpickResultPayload::turn,
            ByteBufCodecs.VAR_INT, LockpickResultPayload::usesLeft,
            ByteBufCodecs.VAR_INT, LockpickResultPayload::spare,
            ByteBufCodecs.VAR_INT, LockpickResultPayload::flags,
            LockpickResultPayload::new);

    public static LockpickResultPayload close() {
        return new LockpickResultPayload(0, 0, 0, CLOSED);
    }

    public boolean broke() {
        return (flags & BROKE) != 0;
    }

    public boolean opened() {
        return (flags & OPENED) != 0;
    }

    public boolean heard() {
        return (flags & HEARD) != 0;
    }

    public boolean closed() {
        return (flags & CLOSED) != 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
