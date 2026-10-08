package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: whether the player owes a guard's fine, to which village, how much, and the seconds left to pay. */
public record FineStatusPayload(boolean pending, String village, int cost, int secondsLeft) implements CustomPacketPayload {
    public static final Type<FineStatusPayload> TYPE = new Type<>(Fealty.id("fine_status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FineStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FineStatusPayload::pending,
            ByteBufCodecs.stringUtf8(256), FineStatusPayload::village,
            ByteBufCodecs.VAR_INT, FineStatusPayload::cost,
            ByteBufCodecs.VAR_INT, FineStatusPayload::secondsLeft,
            FineStatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
