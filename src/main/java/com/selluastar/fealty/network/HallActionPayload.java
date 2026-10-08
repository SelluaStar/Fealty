package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.lordship.LordshipManager;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: a lord's choice in the Village Hall ({@code open}, {@code tax}, {@code collect}, {@code feast}, {@code recruit}). */
public record HallActionPayload(String village, String action, int argument) implements CustomPacketPayload {
    public static final Type<HallActionPayload> TYPE = new Type<>(Fealty.id("hall_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HallActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), HallActionPayload::village,
            ByteBufCodecs.stringUtf8(16), HallActionPayload::action,
            ByteBufCodecs.VAR_INT, HallActionPayload::argument,
            HallActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HallActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LordshipManager.hallAction(player, payload.village(), payload.action(), payload.argument());
        }
    }
}
