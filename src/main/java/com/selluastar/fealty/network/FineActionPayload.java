package com.selluastar.fealty.network;

import com.selluastar.fealty.Fealty;
import com.selluastar.fealty.crime.Fines;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the Journal's buttons for a guard's fine ({@code pay} or {@code refuse}). */
public record FineActionPayload(String action) implements CustomPacketPayload {
    public static final Type<FineActionPayload> TYPE = new Type<>(Fealty.id("fine_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FineActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), FineActionPayload::action,
            FineActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FineActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        switch (payload.action()) {
            case "pay" -> Fines.payPending(player);
            case "refuse" -> Fines.refusePending(player);
            default -> {
            }
        }
    }
}
