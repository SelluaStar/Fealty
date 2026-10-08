package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: start picking the lock at {@code pos}. Where the lock gives stays on the server. */
public record OpenLockpickPayload(BlockPos pos, boolean coffer, int usesLeft, int spare) implements CustomPacketPayload {
    public static final Type<OpenLockpickPayload> TYPE = new Type<>(Fealty.id("open_lockpick"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenLockpickPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenLockpickPayload::pos,
            ByteBufCodecs.BOOL, OpenLockpickPayload::coffer,
            ByteBufCodecs.VAR_INT, OpenLockpickPayload::usesLeft,
            ByteBufCodecs.VAR_INT, OpenLockpickPayload::spare,
            OpenLockpickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
